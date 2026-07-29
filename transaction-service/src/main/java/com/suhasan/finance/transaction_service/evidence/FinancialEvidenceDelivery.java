package com.suhasan.finance.transaction_service.evidence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_evidence_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialEvidenceDelivery {

    @Id
    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialEvidenceDestination destination;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String payload;

    @Column(name = "delivered_at", nullable = false)
    private LocalDateTime deliveredAt;

    public static FinancialEvidenceDelivery create(
            UUID eventId,
            FinancialEvidenceDestination destination,
            String payload,
            LocalDateTime deliveredAt) {
        FinancialEvidenceDelivery delivery = new FinancialEvidenceDelivery();
        delivery.deliveryId = UUID.randomUUID();
        delivery.eventId = eventId;
        delivery.destination = destination;
        delivery.dedupeKey = eventId + ":" + destination;
        delivery.payload = payload;
        delivery.deliveredAt = deliveredAt;
        return delivery;
    }
}
