package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.Beneficiary;
import com.suhasan.finance.account_service.entity.BeneficiaryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, String> {
    Page<Beneficiary> findByUserId(String userId, Pageable pageable);

    Page<Beneficiary> findByUserIdAndStatus(String userId, BeneficiaryStatus status, Pageable pageable);

    Optional<Beneficiary> findByBeneficiaryIdAndUserId(String beneficiaryId, String userId);

    boolean existsByUserIdAndDestinationAccountIdAndCurrencyAndStatus(
            String userId, String destinationAccountId, String currency, BeneficiaryStatus status);
}
