package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@SuppressWarnings({
        "PMD.AvoidInstantiatingObjectsInLoops", // Each claimed notification requires an independent receipt.
        "PMD.AvoidLiteralsInIfCondition" // Receipt classification is a provider protocol value.
})
public class NotificationProviderDispatcher {
    private final NotificationRepository notifications;
    private final NotificationProviderReceiptRepository receipts;
    private final NotificationProvider provider;
    private final Counter terminalFailures;
    private final Counter itemFailures;

    @Value("${integration.notification.dispatch-batch-size:100}")
    private int batchSize;

    public NotificationProviderDispatcher(
            final NotificationRepository notifications,
            final NotificationProviderReceiptRepository receipts,
            final NotificationProvider provider,
            final MeterRegistry meterRegistry) {
        this.notifications = notifications;
        this.receipts = receipts;
        this.provider = provider;
        this.terminalFailures = meterRegistry.counter("notification.provider.terminal.failures");
        this.itemFailures = meterRegistry.counter("notification.provider.item.failures");
        Gauge.builder("notification.provider.backlog", notifications,
                        repository -> repository.countUnreceipted(provider.health().provider()))
                .register(meterRegistry);
        Gauge.builder("notification.provider.terminal.failures.current", receipts,
                        repository -> repository.countByReconciliationStatus("TERMINAL_UNRECONCILED"))
                .register(meterRegistry);
        Gauge.builder("notification.provider.oldest.age.seconds", notifications, repository -> {
                    final LocalDateTime oldest = repository.oldestUnreceipted(provider.health().provider());
                    return oldest == null ? 0D
                            : java.time.Duration.between(oldest, LocalDateTime.now()).toSeconds();
                }).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${integration.notification.dispatch-delay-ms:10000}",
            initialDelayString = "${integration.notification.dispatch-initial-delay-ms:10000}")
    @Transactional
    public void dispatchUnreceipted() {
        final String providerName = provider.health().provider();
        for (final var notification : notifications.claimUnreceipted(providerName, Math.max(1, batchSize))) {
            try {
                final var result = provider.deliver(notification);
                final NotificationProviderReceipt receipt = new NotificationProviderReceipt();
                receipt.setNotificationId(notification.getNotificationId());
                receipt.setDeliveryId(notification.getDeliveryId());
                receipt.setProvider(result.provider());
                receipt.setProviderReceiptId(result.providerReceiptId());
                receipt.setClassification(result.classification().name());
                receipt.setReconciliationStatus(result.reconciliationStatus());
                receipt.setAttemptedAt(LocalDateTime.ofInstant(result.attemptedAt(), java.time.ZoneOffset.UTC));
                receipt.setDetail(sanitize(result.detail()));
                receipts.saveAndFlush(receipt);
                if ("REJECTED".equals(receipt.getClassification())) terminalFailures.increment();
            } catch (RuntimeException failure) {
                // One provider failure cannot starve later notifications in the claimed batch.
                itemFailures.increment();
                final NotificationProviderReceipt receipt = new NotificationProviderReceipt();
                receipt.setNotificationId(notification.getNotificationId());
                receipt.setDeliveryId(notification.getDeliveryId());
                receipt.setProvider(providerName);
                receipt.setClassification("UNAVAILABLE");
                receipt.setReconciliationStatus("UNRECONCILED");
                receipt.setAttemptedAt(LocalDateTime.now());
                receipt.setDetail(sanitize(failure.getClass().getSimpleName()));
                try {
                    receipts.saveAndFlush(receipt);
                } catch (RuntimeException ignored) {
                    // A concurrent replica won the unique receipt claim.
                }
            }
        }
    }

    @Transactional
    public void replay(final long receiptId) {
        final NotificationProviderReceipt receipt = receipts.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Provider receipt not found"));
        if (!"TERMINAL_UNRECONCILED".equals(receipt.getReconciliationStatus())) {
            throw new IllegalStateException("Only terminal failures may be replayed");
        }
        receipts.delete(receipt);
    }

    private String sanitize(final String value) {
        if (value == null) return null;
        final String safe = value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
