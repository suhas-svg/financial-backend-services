package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RiskAlertRepository extends JpaRepository<RiskAlert, String>, JpaSpecificationExecutor<RiskAlert> {

    boolean existsByDedupeKeyAndStatus(String dedupeKey, RiskAlertStatus status);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndCreatedAtBetween(RiskAlertStatus status, LocalDateTime from, LocalDateTime to);

    long countBySeverityAndCreatedAtBetween(RiskAlertSeverity severity, LocalDateTime from, LocalDateTime to);
}
