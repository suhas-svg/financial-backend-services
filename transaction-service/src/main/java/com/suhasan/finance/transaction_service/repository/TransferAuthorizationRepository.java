package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.TransferAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TransferAuthorizationRepository extends JpaRepository<TransferAuthorization, String> {
    Optional<TransferAuthorization> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TransferAuthorization a where a.authorizationId = :id")
    Optional<TransferAuthorization> findByIdWithLock(@Param("id") String id);
}
