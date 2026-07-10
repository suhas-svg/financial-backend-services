package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.MfaMethod;
import com.suhasan.finance.account_service.entity.MfaMethodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MfaMethodRepository extends JpaRepository<MfaMethod, Long> {
    Optional<MfaMethod> findByUserIdAndMethodType(String userId, String methodType);
    Optional<MfaMethod> findByUserIdAndMethodTypeAndStatus(String userId, String methodType, MfaMethodStatus status);
}
