package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationProviderRetryDispatcherTest {
    @Test
    void providerFailureForOneReceiptDoesNotAbortLaterEligibleReceipt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        NotificationProvider provider = mock(NotificationProvider.class);
        NotificationProviderRetryDispatcher dispatcher =
                new NotificationProviderRetryDispatcher(jdbc, notifications, provider);
        ReflectionTestUtils.setField(dispatcher, "maxAttempts", 5);
        ReflectionTestUtils.setField(dispatcher, "initialBackoffSeconds", 30L);
        ReflectionTestUtils.setField(dispatcher, "maxBackoffSeconds", 900L);

        Map<String, Object> first = row(1L, 101L);
        Map<String, Object> second = row(2L, 102L);
        Notification firstNotification = Notification.builder().notificationId(101L).build();
        Notification secondNotification = Notification.builder().notificationId(102L).build();
        when(jdbc.queryForList(any(String.class), any(Object.class))).thenReturn(List.of(first, second));
        when(notifications.findById(101L)).thenReturn(Optional.of(firstNotification));
        when(notifications.findById(102L)).thenReturn(Optional.of(secondNotification));
        when(provider.deliver(firstNotification)).thenThrow(new IllegalStateException("provider unavailable"));
        when(provider.deliver(secondNotification)).thenReturn(new NotificationProvider.ProviderReceipt(
                "test-provider", "provider-2", NotificationProvider.Classification.ACCEPTED,
                "RECONCILED", Instant.now(), "accepted"));

        dispatcher.retryDue();

        verify(provider).deliver(firstNotification);
        verify(provider).deliver(secondNotification);
    }

    private static Map<String, Object> row(long receiptId, long notificationId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("receipt_id", receiptId);
        row.put("notification_id", notificationId);
        row.put("classification", "UNAVAILABLE");
        row.put("attempt_count", 1);
        return row;
    }
}
