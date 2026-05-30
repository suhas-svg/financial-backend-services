package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import com.suhasan.finance.transaction_service.entity.TransactionDispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface TransactionDisputeRepository extends JpaRepository<TransactionDispute, String>, JpaSpecificationExecutor<TransactionDispute> {

    @Query("select count(d) > 0 from TransactionDispute d where d.transactionId = :transactionId and d.status in (com.suhasan.finance.transaction_service.entity.DisputeStatus.OPEN, com.suhasan.finance.transaction_service.entity.DisputeStatus.IN_REVIEW)")
    boolean existsActiveByTransactionId(@Param("transactionId") String transactionId);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusAndCreatedAtBetween(DisputeStatus status, LocalDateTime from, LocalDateTime to);

    long countByAssignedToIsNullAndCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
