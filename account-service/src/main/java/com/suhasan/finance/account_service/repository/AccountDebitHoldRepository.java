package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.AccountDebitHold;
import com.suhasan.finance.account_service.entity.DebitHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDebitHoldRepository extends JpaRepository<AccountDebitHold, String> {
    boolean existsByAccountIdAndStatus(Long accountId, DebitHoldStatus status);
}
