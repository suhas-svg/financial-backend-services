package com.suhasan.finance.account_service.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationProviderReceiptRepository extends JpaRepository<NotificationProviderReceipt, Long> {
    Optional<NotificationProviderReceipt> findByNotificationIdAndProvider(Long notificationId, String provider);
    long countByClassification(String classification);
    long countByReconciliationStatus(String reconciliationStatus);
}
