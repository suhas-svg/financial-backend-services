package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.TransactionLimit;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, Long> {
    
    Optional<TransactionLimit> findByAccountTypeAndTransactionTypeAndActiveTrue(
        String accountType, TransactionType transactionType);
    
    List<TransactionLimit> findByAccountTypeAndActiveTrue(String accountType);
    
    List<TransactionLimit> findByTransactionTypeAndActiveTrue(TransactionType transactionType);
    
    List<TransactionLimit> findByActiveTrue();
}