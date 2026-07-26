package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "integration.notification.provider", havingValue = "local-inbox", matchIfMissing = true)
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // Provider identifier is intentionally repeated in evidence records.
public class LocalInboxNotificationProvider implements NotificationProvider {
    @Override
    public ProviderReceipt deliver(final Notification notification) {
        return new ProviderReceipt("local-inbox", "local-" + notification.getNotificationId(),
                Classification.ACCEPTED, "MATCHED", Instant.now(),
                "Non-production in-app adapter; no external delivery claimed");
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth("local-inbox", true, true, "NON_PRODUCTION_ADAPTER",
                Instant.now(), "local-inbox");
    }
}
