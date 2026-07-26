package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.repository.NotificationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationProviderDispatcherTest {
    private NotificationRepository notifications;
    private NotificationProviderReceiptRepository receipts;
    private NotificationProvider provider;
    private SimpleMeterRegistry meters;
    private NotificationProviderDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        receipts = mock(NotificationProviderReceiptRepository.class);
        provider = mock(NotificationProvider.class);
        meters = new SimpleMeterRegistry();
        when(provider.health()).thenReturn(new NotificationProvider.ProviderHealth(
                "provider", true, true, "HEALTHY", Instant.now(), "evidence"));
        dispatcher = new NotificationProviderDispatcher(notifications, receipts, provider, meters);
        ReflectionTestUtils.setField(dispatcher, "batchSize", 0);
    }

    @Test
    void dispatchesAcceptedAndRejectedReceiptsWithSanitizedBoundedEvidence() {
        Notification accepted = notification(1L, "delivery-1");
        Notification rejected = notification(2L, "delivery-2");
        when(notifications.claimUnreceipted("provider", 1)).thenReturn(List.of(accepted, rejected));
        when(provider.deliver(accepted)).thenReturn(receipt(
                NotificationProvider.Classification.ACCEPTED, null, null));
        when(provider.deliver(rejected)).thenReturn(receipt(
                NotificationProvider.Classification.REJECTED, "UNRECONCILED", "x\r\n" + "a".repeat(600)));

        dispatcher.dispatchUnreceipted();

        var captor = org.mockito.ArgumentCaptor.forClass(NotificationProviderReceipt.class);
        verify(receipts, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues().get(0).getDetail()).isNull();
        assertThat(captor.getAllValues().get(1).getDetail()).doesNotContain("\r", "\n").hasSize(500);
        assertThat(meters.get("notification.provider.terminal.failures").counter().count()).isEqualTo(1);
    }

    @Test
    void recordsProviderFailureAndToleratesConcurrentReceiptWinner() {
        Notification failed = notification(3L, "delivery-3");
        when(notifications.claimUnreceipted("provider", 1)).thenReturn(List.of(failed));
        when(provider.deliver(failed)).thenThrow(new IllegalStateException("secret value must not be logged"));
        doThrow(new IllegalStateException("unique winner")).when(receipts).saveAndFlush(any());

        dispatcher.dispatchUnreceipted();

        assertThat(meters.get("notification.provider.item.failures").counter().count()).isEqualTo(1);
    }

    @Test
    void gaugeCallbacksHandleEmptyAndAgedBacklog() {
        when(notifications.countUnreceipted("provider")).thenReturn(4L);
        when(receipts.countByReconciliationStatus("TERMINAL_UNRECONCILED")).thenReturn(2L);
        when(notifications.oldestUnreceipted("provider")).thenReturn(null);
        assertThat(meters.get("notification.provider.backlog").gauge().value()).isEqualTo(4);
        assertThat(meters.get("notification.provider.terminal.failures.current").gauge().value()).isEqualTo(2);
        assertThat(meters.get("notification.provider.oldest.age.seconds").gauge().value()).isZero();
        when(notifications.oldestUnreceipted("provider")).thenReturn(LocalDateTime.now().minusSeconds(5));
        assertThat(meters.get("notification.provider.oldest.age.seconds").gauge().value()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void replayRequiresExistingTerminalReceipt() {
        when(receipts.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> dispatcher.replay(1L)).isInstanceOf(IllegalArgumentException.class);

        NotificationProviderReceipt active = new NotificationProviderReceipt();
        active.setReconciliationStatus("MATCHED");
        when(receipts.findById(2L)).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> dispatcher.replay(2L)).isInstanceOf(IllegalStateException.class);

        NotificationProviderReceipt terminal = new NotificationProviderReceipt();
        terminal.setReconciliationStatus("TERMINAL_UNRECONCILED");
        when(receipts.findById(3L)).thenReturn(Optional.of(terminal));
        dispatcher.replay(3L);
        verify(receipts).delete(terminal);
    }

    private Notification notification(long id, String deliveryId) {
        return Notification.builder().notificationId(id).deliveryId(deliveryId).build();
    }

    private NotificationProvider.ProviderReceipt receipt(
            NotificationProvider.Classification classification, String reconciliation, String detail) {
        return new NotificationProvider.ProviderReceipt(
                "provider", "receipt", classification, reconciliation, Instant.now(), detail);
    }
}
