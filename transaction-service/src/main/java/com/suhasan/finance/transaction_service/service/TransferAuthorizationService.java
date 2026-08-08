package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.entity.TransferAuthorization;
import com.suhasan.finance.transaction_service.entity.TransferAuthorizationStatus;
import com.suhasan.finance.transaction_service.exception.InsufficientFundsException;
import com.suhasan.finance.transaction_service.exception.TransactionLimitExceededException;
import com.suhasan.finance.transaction_service.repository.TransferAuthorizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferAuthorizationService {
    private final TransferAuthorizationPolicy policy;
    private final TransferAuthorizationRepository authorizationRepository;
    private final ResilientAccountServiceClient accountServiceClient;
    private final TransactionService transactionService;
    private final AuditService auditService;
    private final TransferAuthorizationStateService authorizationStateService;

    @Transactional
    public TransactionResponse submit(TransferRequest request, String userId, String idempotencyKey) {
        String fingerprint = TransferAuthorizationFingerprint.of(userId, request);
        String normalizedKey = normalizeKey(idempotencyKey);
        if (normalizedKey != null) {
            var existing = authorizationRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey);
            if (existing.isPresent()) {
                assertFingerprint(existing.get(), fingerprint);
                return response(existing.get());
            }
        }

        List<TransferAuthorizationReason> reasons = policy.evaluate(request, userId);
        if (reasons.isEmpty()) {
            return transactionService.processTransfer(request, userId, normalizedKey);
        }

        if (normalizedKey == null) {
            normalizedKey = "step-up:" + UUID.randomUUID();
        }
        StepUpClientDtos.CreateChallengeResponse challenge = accountServiceClient.createStepUpChallenge(
                new StepUpClientDtos.CreateChallengeRequest(userId, "TRANSFER", fingerprint));
        TransferAuthorization authorization = new TransferAuthorization();
        authorization.setUserId(userId);
        authorization.setIdempotencyKey(normalizedKey);
        authorization.setActionFingerprint(fingerprint);
        authorization.setChallengeId(challenge.challengeId());
        authorization.setFromAccountId(request.getFromAccountId());
        authorization.setToAccountId(request.getToAccountId());
        authorization.setBeneficiaryId(request.getBeneficiaryId());
        authorization.setAmount(request.getAmount());
        authorization.setCurrency(request.getCurrency());
        authorization.setDescription(request.getDescription());
        authorization.setReference(request.getReference());
        authorization.setReasonCodes(reasons.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(""));
        authorization.setStatus(TransferAuthorizationStatus.PENDING);
        authorization.setExpiresAt(challenge.expiresAt());
        authorization = authorizationRepository.save(authorization);
        auditService.logSecurityEvent("STEP_UP_REQUIRED", userId,
                "authorizationId=" + authorization.getAuthorizationId() + ", reasons=" + authorization.getReasonCodes(), null);
        notifyBestEffort(authorization, "SECURITY_ACTION_REQUIRED", "WARNING",
                "Additional verification required", "Verify this transfer before it expires.");
        return response(authorization);
    }

    public TransactionResponse authorize(String authorizationId, String userId, String proof) {
        final TransferAuthorization authorization;
        try {
            authorization = authorizationStateService.authorizeProof(authorizationId, userId, proof);
        } catch (IllegalStateException e) {
            if ("Transfer authorization has expired".equals(e.getMessage())) {
                auditService.logSecurityEvent("STEP_UP_EXPIRED", userId,
                        "authorizationId=" + authorizationId, null);
            }
            throw e;
        }
        if (authorization.getStatus() == TransferAuthorizationStatus.COMPLETED) {
            return transactionService.getTransaction(authorization.getExecutedTransactionId());
        }
        try {
            TransactionResponse executed = transactionService.processTransfer(toRequest(authorization), userId,
                    authorization.getIdempotencyKey());
            authorizationStateService.markCompleted(authorizationId, userId, executed.getTransactionId());
            auditService.logSecurityEvent("STEP_UP_AUTHORIZED", userId,
                    "authorizationId=" + authorizationId + ", transactionId=" + executed.getTransactionId(), null);
            notifyBestEffort(authorization, "TRANSFER_AUTHORIZED", "SUCCESS",
                    "Transfer authorized", "Your verified transfer has been processed.");
            return executed;
        } catch (Exception e) {
            // A consumed proof remains retryable only for an ambiguous downstream
            // outcome. Deterministic validation/funds failures are terminal so the
            // customer is not left with a hanging authorization dialog.
            boolean deterministic = e instanceof InsufficientFundsException
                    || e instanceof TransactionLimitExceededException
                    || e instanceof IllegalArgumentException;
            if (deterministic) {
                authorizationStateService.markFailed(authorizationId, userId);
            }
            auditService.logSecurityEvent(deterministic ? "STEP_UP_EXECUTION_FAILED" : "STEP_UP_EXECUTION_PENDING",
                    userId, "authorizationId=" + authorizationId + ", reason=" + e.getMessage(), null);
            if (deterministic) {
                notifyBestEffort(authorization, "TRANSFER_AUTHORIZED", "ERROR",
                        "Transfer could not be completed", "No money moved. Review the transfer details before trying again.");
            }
            throw e;
        }
    }

    @Transactional
    public TransactionResponse cancel(String authorizationId, String userId) {
        TransferAuthorization authorization = lockedOwned(authorizationId, userId);
        if (authorization.getStatus() == TransferAuthorizationStatus.COMPLETED) {
            throw new IllegalStateException("Completed transfer cannot be cancelled");
        }
        authorization.setStatus(TransferAuthorizationStatus.CANCELLED);
        authorizationRepository.save(authorization);
        auditService.logSecurityEvent("STEP_UP_CANCELLED", userId, "authorizationId=" + authorizationId, null);
        return response(authorization);
    }

    @Transactional(readOnly = true)
    public TransactionResponse status(String authorizationId, String userId) {
        TransferAuthorization authorization = authorizationRepository.findById(authorizationId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer authorization not found"));
        if (!authorization.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Transfer authorization not found");
        }
        return response(authorization);
    }

    private TransferAuthorization lockedOwned(String id, String userId) {
        TransferAuthorization authorization = authorizationRepository.findByIdWithLock(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer authorization not found"));
        if (!authorization.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Transfer authorization not found");
        }
        return authorization;
    }

    private void assertFingerprint(TransferAuthorization authorization, String fingerprint) {
        if (!authorization.getActionFingerprint().equals(fingerprint)) {
            throw new IllegalStateException("Idempotency key was already used with a different transfer");
        }
    }

    private TransferRequest toRequest(TransferAuthorization authorization) {
        return TransferRequest.builder()
                .fromAccountId(authorization.getFromAccountId())
                .toAccountId(authorization.getToAccountId())
                .beneficiaryId(authorization.getBeneficiaryId())
                .amount(authorization.getAmount())
                .currency(authorization.getCurrency())
                .description(authorization.getDescription())
                .reference(authorization.getReference())
                .build();
    }

    private TransactionResponse response(TransferAuthorization authorization) {
        if (authorization.getStatus() == TransferAuthorizationStatus.COMPLETED
                && authorization.getExecutedTransactionId() != null) {
            return transactionService.getTransaction(authorization.getExecutedTransactionId());
        }
        return TransactionResponse.builder()
                .transactionId(authorization.getAuthorizationId())
                .fromAccountId(authorization.getFromAccountId())
                .toAccountId(authorization.getToAccountId())
                .amount(authorization.getAmount())
                .currency(authorization.getCurrency())
                .type(TransactionType.TRANSFER)
                .status(authorization.getStatus() == TransferAuthorizationStatus.CANCELLED
                        ? TransactionStatus.CANCELLED
                        : authorization.getStatus() == TransferAuthorizationStatus.FAILED
                        ? TransactionStatus.FAILED : TransactionStatus.PENDING)
                .processingState(authorization.getStatus() == TransferAuthorizationStatus.CANCELLED
                        ? "AUTHORIZATION_CANCELLED"
                        : authorization.getStatus() == TransferAuthorizationStatus.FAILED
                        ? "AUTHORIZATION_FAILED"
                        : authorization.getStatus() == TransferAuthorizationStatus.AUTHORIZED
                        ? "AUTHORIZATION_ACCEPTED_RETRYABLE" : "AWAITING_AUTHORIZATION")
                .description(authorization.getDescription())
                .reference(authorization.getReference())
                .createdBy(authorization.getUserId())
                .createdAt(LocalDateTime.ofInstant(authorization.getCreatedAt(), ZoneOffset.UTC))
                .idempotencyKey(authorization.getIdempotencyKey())
                .authorizationRequired(authorization.getStatus() == TransferAuthorizationStatus.PENDING
                        || authorization.getStatus() == TransferAuthorizationStatus.AUTHORIZED)
                .authorizationChallengeId(authorization.getChallengeId())
                .authorizationExpiresAt(authorization.getExpiresAt())
                .authorizationReasons(parseReasons(authorization.getReasonCodes()))
                .build();
    }

    private List<String> parseReasons(String reasons) {
        return reasons == null || reasons.isBlank() ? List.of() : Arrays.asList(reasons.split(","));
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 128) throw new IllegalArgumentException("Idempotency key is too long");
        return normalized;
    }

    private void notifyBestEffort(TransferAuthorization authorization, String type, String severity,
                                  String title, String message) {
        try {
            accountServiceClient.createNotification(ResilientAccountServiceClient.NotificationRequest.builder()
                    .userId(authorization.getUserId()).type(type).severity(severity)
                    .title(title).message(message).sourceType("TRANSACTION")
                    .sourceId(authorization.getAuthorizationId())
                    .dedupeKey("step-up:" + authorization.getAuthorizationId() + ":" + type).build());
        } catch (Exception ignored) {
            // Notification delivery must never alter authorization state.
        }
    }
}
