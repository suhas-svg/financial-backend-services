package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.StepUpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface StepUpChallengeRepository extends JpaRepository<StepUpChallenge, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StepUpChallenge> findForUpdateByChallengeId(String challengeId);
}
