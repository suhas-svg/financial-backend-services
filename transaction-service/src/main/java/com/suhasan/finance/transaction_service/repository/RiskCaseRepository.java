package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.RiskCase;
import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface RiskCaseRepository extends JpaRepository<RiskCase, String>, JpaSpecificationExecutor<RiskCase> {

    Optional<RiskCase> findFirstByPrimaryAlertIdAndStatusIn(String primaryAlertId, Collection<RiskCaseStatus> statuses);

    default Optional<RiskCase> findOpenCaseByAlertId(String alertId) {
        return findFirstByPrimaryAlertIdAndStatusIn(alertId, RiskCaseStatus.activeStatuses());
    }

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndCreatedAtBetween(RiskCaseStatus status, LocalDateTime from, LocalDateTime to);

    long countByPriorityAndCreatedAtBetween(RiskCasePriority priority, LocalDateTime from, LocalDateTime to);

    long countByAssignedToIsNullAndCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
