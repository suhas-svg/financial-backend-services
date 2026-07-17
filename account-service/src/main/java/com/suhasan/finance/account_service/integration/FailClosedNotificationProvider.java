package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnMissingBean(NotificationProvider.class)
public class FailClosedNotificationProvider implements NotificationProvider {
    @Override
    public ProviderReceipt deliver(Notification notification) {
        return new ProviderReceipt("unconfigured", null, Classification.UNAVAILABLE,
                "UNRECONCILED", Instant.now(), "Notification provider is not configured");
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth("unconfigured", false, false, "FAIL_CLOSED",
                Instant.now(), "missing-provider");
    }
}
