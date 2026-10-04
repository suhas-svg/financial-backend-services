package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.AccountStatusAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AccountStatusAuditEventRepository extends JpaRepository<AccountStatusAuditEvent, Long> {

    List<AccountStatusAuditEvent> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("select e from AccountStatusAuditEvent e order by e.createdAt desc")
    List<AccountStatusAuditEvent> findRecent(Pageable pageable);
}
