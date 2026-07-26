package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotificationProviderRetryDispatcher {
    private static final Set<String> RETRYABLE = Set.of("TIMEOUT", "UNAVAILABLE", "RATE_LIMITED");
    private final JdbcTemplate jdbc;
    private final NotificationRepository notifications;
    private final NotificationProvider provider;

    @Value("${integration.notification.max-attempts:5}") private int maxAttempts;
    @Value("${integration.notification.initial-backoff-seconds:30}") private long initialBackoffSeconds;
    @Value("${integration.notification.max-backoff-seconds:900}") private long maxBackoffSeconds;

    @Scheduled(fixedDelayString = "${integration.notification.retry-delay-ms:10000}",
            initialDelayString = "${integration.notification.retry-initial-delay-ms:15000}")
    @Transactional
    public void retryDue() {
        final Instant now = Instant.now();
        final var due = jdbc.queryForList("""
                SELECT receipt_id,notification_id,classification,attempt_count
                  FROM notification_provider_receipts
                 WHERE terminal_at IS NULL
                   AND (classification IN ('TIMEOUT','UNAVAILABLE','RATE_LIMITED'))
                   AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                 ORDER BY receipt_id FOR UPDATE SKIP LOCKED LIMIT 50
                """, Timestamp.from(now));
        for (final var row : due) {
            retryOne(row, now);
        }
    }

    private void retryOne(final java.util.Map<String, Object> row, final Instant now) {
        final long receiptId = ((Number) row.get("receipt_id")).longValue();
        final long notificationId = ((Number) row.get("notification_id")).longValue();
        final int attempts = ((Number) row.get("attempt_count")).intValue() + 1;
        try {
            final var notification = notifications.findById(notificationId).orElse(null);
            if (notification == null) {
                terminal(receiptId, attempts, now, "Notification row is missing");
                return;
            }
            final var result = provider.deliver(notification);
            final String classification = result.classification().name();
            if (classification.equals("ACCEPTED")) {
                jdbc.update("""
                        UPDATE notification_provider_receipts
                           SET provider_receipt_id=?,classification=?,reconciliation_status=?,
                               attempted_at=?,attempt_count=?,next_attempt_at=NULL,reconciled_at=?
                         WHERE receipt_id=?
                        """, result.providerReceiptId(), classification, result.reconciliationStatus(),
                        utc(result.attemptedAt()), attempts, utc(now), receiptId);
            } else if (!RETRYABLE.contains(classification) || attempts >= Math.max(1, maxAttempts)) {
                terminal(receiptId, attempts, now, sanitize(result.detail()));
            } else {
                scheduleRetry(receiptId, attempts, now, classification, result.attemptedAt(), result.detail());
            }
        } catch (RuntimeException failure) {
            if (attempts >= Math.max(1, maxAttempts)) {
                terminal(receiptId, attempts, now, sanitize(failure.getClass().getSimpleName()));
            } else {
                scheduleRetry(receiptId, attempts, now, "UNAVAILABLE", now, failure.getClass().getSimpleName());
            }
        }
    }

    private void scheduleRetry(final long receiptId, final int attempts, final Instant now, final String classification,
                               final Instant attemptedAt, final String detail) {
        final long multiplier = 1L << Math.min(20, attempts - 1);
        final long delay = Math.min(Math.max(1, maxBackoffSeconds),
                Math.max(1, initialBackoffSeconds) * multiplier);
        jdbc.update("""
                UPDATE notification_provider_receipts
                   SET classification=?,reconciliation_status=?,attempted_at=?,attempt_count=?,
                       next_attempt_at=?,detail=?
                 WHERE receipt_id=?
                """, classification, "UNRECONCILED", utc(attemptedAt), attempts,
                utc(now.plusSeconds(delay)), sanitize(detail), receiptId);
    }

    private void terminal(final long receiptId, final int attempts, final Instant now, final String detail) {
        jdbc.update("""
                UPDATE notification_provider_receipts
                   SET classification='REJECTED',reconciliation_status='TERMINAL_UNRECONCILED',
                       attempt_count=?,next_attempt_at=NULL,terminal_at=?,detail=?
                 WHERE receipt_id=?
                """, attempts, utc(now), detail, receiptId);
    }
    private LocalDateTime utc(final Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private String sanitize(final String value) {
        if (value == null) return null;
        final String safe = value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
