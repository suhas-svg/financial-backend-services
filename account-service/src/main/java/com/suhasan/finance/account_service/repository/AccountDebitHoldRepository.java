package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.AccountDebitHold;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDebitHoldRepository extends JpaRepository<AccountDebitHold, String> {
}
