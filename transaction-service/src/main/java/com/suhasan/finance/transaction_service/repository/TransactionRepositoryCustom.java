package com.suhasan.finance.transaction_service.repository;

import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionRepositoryCustom {

    Page<Transaction> findTransactionsWithFilters(
            String accountId,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String descriptionPattern,
            String reference,
            String createdBy,
            Pageable pageable);
}
