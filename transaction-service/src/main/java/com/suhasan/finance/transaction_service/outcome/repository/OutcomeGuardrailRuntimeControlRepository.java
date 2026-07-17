package com.suhasan.finance.transaction_service.outcome.repository;

import com.suhasan.finance.transaction_service.outcome.domain.OutcomeGuardrailRuntimeControl;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OutcomeGuardrailRuntimeControlRepository extends JpaRepository<OutcomeGuardrailRuntimeControl, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OutcomeGuardrailRuntimeControl c where c.controlId = :id")
    Optional<OutcomeGuardrailRuntimeControl> lockById(@Param("id") String id);
}
