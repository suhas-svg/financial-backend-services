package com.suhasan.finance.account_service.integration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_provider_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_provider_receipt",
                columnNames = {"notification_id", "provider"}))
@Getter @Setter @NoArgsConstructor
public class NotificationProviderReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long receiptId;
    @Column(name = "notification_id", nullable = false) private Long notificationId;
    @Column(name = "delivery_id", length = 36) private String deliveryId;
    @Column(nullable = false, length = 80) private String provider;
    @Column(name = "provider_receipt_id", length = 160) private String providerReceiptId;
    @Column(nullable = false, length = 30) private String classification;
    @Column(name = "reconciliation_status", nullable = false, length = 30) private String reconciliationStatus;
    @Column(name = "attempted_at", nullable = false) private LocalDateTime attemptedAt;
    @Column(length = 500) private String detail;
}
