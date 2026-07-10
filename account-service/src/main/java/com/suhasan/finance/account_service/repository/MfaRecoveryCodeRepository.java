package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {
    List<MfaRecoveryCode> findByMfaMethodIdAndUsedAtIsNull(Long mfaMethodId);
    void deleteByMfaMethodId(Long mfaMethodId);
    long countByMfaMethodIdAndUsedAtIsNull(Long mfaMethodId);
}
