package com.suhasan.finance.account_service.repository;
import com.suhasan.finance.account_service.entity.SpendingLimitAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SpendingLimitAuditEventRepository extends JpaRepository<SpendingLimitAuditEvent,Long> {}
