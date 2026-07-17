package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Notification;

import java.time.Instant;

/**
 * External channel boundary. Implementations acknowledge provider acceptance only;
 * they never claim that a customer read a message.
 */
public interface NotificationProvider {
    ProviderReceipt deliver(Notification notification);
    ProviderHealth health();

    record ProviderReceipt(String provider, String providerReceiptId, Classification classification,
                           String reconciliationStatus, Instant attemptedAt, String detail) {}
    record ProviderHealth(String provider, boolean configured, boolean healthy, String classification,
                          Instant checkedAt, String evidenceReference) {}
    enum Classification { ACCEPTED, TIMEOUT, UNAVAILABLE, RATE_LIMITED, REJECTED, INVALID_DESTINATION }
}
