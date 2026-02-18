package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.AccountBalanceOperation;
import com.suhasan.finance.account_service.entity.AccountBalanceOperationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountBalanceOperationRepository
        extends JpaRepository<AccountBalanceOperation, AccountBalanceOperationId> {
}
