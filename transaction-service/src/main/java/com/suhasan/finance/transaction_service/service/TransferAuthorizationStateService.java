package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.entity.TransferAuthorization;
import com.suhasan.finance.transaction_service.entity.TransferAuthorizationStatus;
import com.suhasan.finance.transaction_service.repository.TransferAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Owns the durable authorization state transitions around a step-up proof.
 * Proof consumption is deliberately committed before transfer execution starts;
 * execution and its terminal/retryable outcome use separate transactions.
 */
@Service
@RequiredArgsConstructor
public class TransferAuthorizationStateService {
    private final TransferAuthorizationRepository authorizationRepository;
    private final ResilientAccountServiceClient accountServiceClient;

    @Transactional
    public TransferAuthorization authorizeProof(String authorizationId, String userId, String proof) {
        TransferAuthorization authorization = lockedOwned(authorizationId, userId);
        if (authorization.getStatus() == TransferAuthorizationStatus.COMPLETED
                || authorization.getStatus() == TransferAuthorizationStatus.AUTHORIZED) {
            return authorization;
        }
        if (authorization.getStatus() != TransferAuthorizationStatus.PENDING) {
            throw new IllegalStateException("Transfer authorization cannot be completed in its current state");
        }
        if (!authorization.getExpiresAt().isAfter(Instant.now())) {
            authorization.setStatus(TransferAuthorizationStatus.CANCELLED);
            authorizationRepository.save(authorization);
            throw new IllegalStateException("Transfer authorization has expired");
        }

        accountServiceClient.consumeStepUpChallenge(authorization.getChallengeId(),
                new StepUpClientDtos.ConsumeChallengeRequest(userId, authorization.getActionFingerprint(),
                        authorization.getAuthorizationId(), proof));
        authorization.setStatus(TransferAuthorizationStatus.AUTHORIZED);
        authorization.setAuthorizedAt(Instant.now());
        return authorizationRepository.save(authorization);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String authorizationId, String userId, String transactionId) {
        TransferAuthorization authorization = lockedOwned(authorizationId, userId);
        authorization.setStatus(TransferAuthorizationStatus.COMPLETED);
        authorization.setExecutedTransactionId(transactionId);
        authorization.setCompletedAt(Instant.now());
        authorizationRepository.save(authorization);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String authorizationId, String userId) {
        TransferAuthorization authorization = lockedOwned(authorizationId, userId);
        if (authorization.getStatus() != TransferAuthorizationStatus.COMPLETED) {
            authorization.setStatus(TransferAuthorizationStatus.FAILED);
            authorizationRepository.save(authorization);
        }
    }

    private TransferAuthorization lockedOwned(String id, String userId) {
        TransferAuthorization authorization = authorizationRepository.findByIdWithLock(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer authorization not found"));
        if (!authorization.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Transfer authorization not found");
        }
        return authorization;
    }
}
