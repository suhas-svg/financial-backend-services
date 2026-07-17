package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationProviderDispatcher {
    private final NotificationRepository notifications;
    private final NotificationProviderReceiptRepository receipts;
    private final NotificationProvider provider;

    @Scheduled(fixedDelayString = "${integration.notification.dispatch-delay-ms:10000}",
            initialDelayString = "${integration.notification.dispatch-initial-delay-ms:10000}")
    @Transactional
    public void dispatchUnreceipted() {
        for (var notification : notifications.findAll(PageRequest.of(0, 100))) {
            String providerName = provider.health().provider();
            if (receipts.findByNotificationIdAndProvider(notification.getNotificationId(), providerName).isPresent()) continue;
            var result = provider.deliver(notification);
            NotificationProviderReceipt receipt = new NotificationProviderReceipt();
            receipt.setNotificationId(notification.getNotificationId());
            receipt.setDeliveryId(notification.getDeliveryId());
            receipt.setProvider(result.provider());
            receipt.setProviderReceiptId(result.providerReceiptId());
            receipt.setClassification(result.classification().name());
            receipt.setReconciliationStatus(result.reconciliationStatus());
            receipt.setAttemptedAt(LocalDateTime.ofInstant(result.attemptedAt(), java.time.ZoneOffset.UTC));
            receipt.setDetail(sanitize(result.detail()));
            receipts.save(receipt);
        }
    }

    private String sanitize(String value) {
        if (value == null) return null;
        String safe = value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
