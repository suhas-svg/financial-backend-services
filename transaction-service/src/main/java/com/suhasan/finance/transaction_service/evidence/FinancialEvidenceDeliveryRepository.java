package com.suhasan.finance.transaction_service.evidence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinancialEvidenceDeliveryRepository extends JpaRepository<FinancialEvidenceDelivery, UUID> {
    boolean existsByEventIdAndDestination(UUID eventId, FinancialEvidenceDestination destination);
    long countByEventId(UUID eventId);
}
