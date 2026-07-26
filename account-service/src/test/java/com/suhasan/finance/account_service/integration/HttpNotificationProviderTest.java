package com.suhasan.finance.account_service.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.suhasan.finance.account_service.entity.Notification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpNotificationProviderTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void requiresEndpointAndBearerToken() {
        assertThatThrownBy(() -> new HttpNotificationProvider("", "token", "contract", "evidence"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new HttpNotificationProvider("http://localhost", " ", "contract", "evidence"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsReceiptAndDefaultsPendingReconciliation() throws Exception {
        start(200, "{\"receiptId\":\"receipt-1\",\"reconciliationStatus\":null}");
        var result = provider().deliver(notification(null));
        assertThat(result.classification()).isEqualTo(NotificationProvider.Classification.ACCEPTED);
        assertThat(result.providerReceiptId()).isEqualTo("receipt-1");
        assertThat(result.reconciliationStatus()).isEqualTo("PENDING");
    }

    @Test
    void rejectsMissingReceiptAndUsesExplicitDeliveryId() throws Exception {
        AtomicInteger bodyMatches = new AtomicInteger();
        start(exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"deliveryId\":\"delivery-9\"")) bodyMatches.incrementAndGet();
            respond(exchange, 200, "{\"receiptId\":\" \",\"reconciliationStatus\":\"MATCHED\"}");
        });
        var result = provider().deliver(notification("delivery-9"));
        assertThat(bodyMatches).hasValue(1);
        assertThat(result.classification()).isEqualTo(NotificationProvider.Classification.REJECTED);
    }

    @Test
    void classifiesRateLimitServerDestinationAndGenericFailures() throws Exception {
        assertStatus(429, NotificationProvider.Classification.RATE_LIMITED);
        assertStatus(503, NotificationProvider.Classification.UNAVAILABLE);
        assertStatus(404, NotificationProvider.Classification.INVALID_DESTINATION);
        assertStatus(422, NotificationProvider.Classification.INVALID_DESTINATION);
        assertStatus(400, NotificationProvider.Classification.REJECTED);
    }

    @Test
    void reportsHealthyAndUnavailableHealthEvidence() throws Exception {
        start(200, "ok");
        assertThat(provider().health()).satisfies(health -> {
            assertThat(health.healthy()).isTrue();
            assertThat(health.evidenceReference()).isEqualTo("evidence");
        });
        stop();
        server = null;
        var unavailable = new HttpNotificationProvider("http://127.0.0.1:1", "token", "contract", "evidence").health();
        assertThat(unavailable.healthy()).isFalse();
        assertThat(unavailable.classification()).isEqualTo("UNAVAILABLE");
    }

    private void assertStatus(int status, NotificationProvider.Classification expected) throws Exception {
        stop();
        start(status, "failure");
        assertThat(provider().deliver(notification(null)).classification()).isEqualTo(expected);
    }

    private HttpNotificationProvider provider() {
        return new HttpNotificationProvider(
                "http://127.0.0.1:" + server.getAddress().getPort(), "token", "contract", "evidence");
    }

    private Notification notification(String deliveryId) {
        return Notification.builder().notificationId(9L).deliveryId(deliveryId).userId("customer")
                .title("Title").message("Message").build();
    }

    private void start(int status, String body) throws IOException {
        start(exchange -> respond(exchange, status, body));
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", handler);
        server.createContext("/health", handler);
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
