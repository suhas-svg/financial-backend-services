package com.suhasan.finance.transaction_service.outcome.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.outcome.domain.*;
import com.suhasan.finance.transaction_service.outcome.repository.*;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.*;
import com.suhasan.finance.transaction_service.service.TransferAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OutcomeGuardrailService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final String TERMS_TEXT = "Balance Shield top-ups are customer-invoked same-currency transfers. " +
            "Activation does not move money. Each action requires explicit confirmation and remains subject to " +
            "ownership, account status, balance, spending-limit, risk, MFA, idempotency, and ledger controls. " +
            "Revocation prevents future actions but never reverses a completed transfer.";

    private final OutcomeGuardrailDraftRepository draftRepository;
    private final OutcomeGuardrailPolicyRepository policyRepository;
    private final OutcomeGuardrailExecutionRepository executionRepository;
    private final OutcomeGuardrailRuntimeControlRepository controlRepository;
    private final OutcomeScenarioRepository scenarioRepository;
    private final OutcomeScenarioVersionRepository scenarioVersionRepository;
    private final OutcomeDomainEventRepository eventRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final ResilientAccountServiceClient accountServiceClient;
    private final TransferAuthorizationService transferAuthorizationService;
    private final OutcomeNotificationDeliveryService notificationDeliveryService;
    private final ObjectMapper objectMapper;

    @Value("${outcome-protection.guardrails.terms-version:2026-07-16.1}")
    private String termsVersion;

    public GuardrailTermsResponse terms() {
        return new GuardrailTermsResponse(termsVersion, termsHash(), "Balance Shield executable top-up terms",
                TERMS_TEXT, List.of(
                "I selected the exact funding and protected accounts.",
                "I understand activation alone never moves money.",
                "I must explicitly confirm each top-up and risk checks may require another MFA step.",
                "I can suspend or revoke future actions, but completed transfers remain final ledger history."),
                false);
    }

    @Transactional
    public GuardrailPolicyResponse consent(String guardrailId, GuardrailConsentRequest request,
                                           String userId, String idempotencyKey) {
        if (request == null || !request.confirmed()) {
            throw new IllegalArgumentException("Explicit informed consent is required");
        }
        String key = requireIdempotencyKey(idempotencyKey);
        OutcomeGuardrailDraft draft = draftRepository.lockByGuardrailAndUser(guardrailId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail draft not found"));
        validateExecutableDraft(draft);
        validateTerms(request.termsVersion(), request.termsHash());
        validateConsentLimits(draft, request);
        validateConsentAccounts(draft, request, userId);
        OutcomeScenario scenario = scenarioRepository.findByScenarioIdAndUserId(draft.getScenarioId(), userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail draft not found"));
        OutcomeScenarioVersion scenarioVersion = scenarioVersionRepository
                .findByScenarioIdAndScenarioVersion(scenario.getScenarioId(), scenario.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("Authoritative scenario version is missing"));

        String requestFingerprint = fingerprint(consentFingerprint(draft, request));
        var keyReplay = policyRepository.findByUserIdAndConsentIdempotencyKey(userId, key);
        if (keyReplay.isPresent()) {
            requireSame(keyReplay.get().getConsentRequestFingerprint(), requestFingerprint,
                    "Idempotency-Key was already used for different consent");
            if (!keyReplay.get().getGuardrailId().equals(guardrailId)) {
                throw new IllegalStateException("Idempotency-Key was already used for another guardrail");
            }
            return response(keyReplay.get());
        }
        var existingPolicy = policyRepository.findByGuardrailIdAndUserId(guardrailId, userId);
        if (existingPolicy.isPresent()) {
            requireSame(existingPolicy.get().getConsentRequestFingerprint(), requestFingerprint,
                    "This guardrail already has different consent evidence");
            if (!existingPolicy.get().getConsentIdempotencyKey().equals(key)) {
                throw new IllegalStateException("This guardrail was already consented with another idempotency key");
            }
            return response(existingPolicy.get());
        }

        Instant now = Instant.now();
        String policyId = UUID.randomUUID().toString();
        String activationFingerprint = fingerprint(Map.of(
                "policyId", policyId, "guardrailId", guardrailId,
                "consentFingerprint", requestFingerprint, "termsHash", request.termsHash()));
        StepUpClientDtos.CreateChallengeResponse challenge = accountServiceClient.createStepUpChallenge(
                new StepUpClientDtos.CreateChallengeRequest(userId, "BALANCE_SHIELD_ACTIVATION", activationFingerprint));
        String evidence = json(Map.of(
                "confirmed", true, "termsVersion", request.termsVersion(), "termsHash", request.termsHash(),
                "recordedAt", now.toString(), "idempotencyKey", key,
                "backgroundExecution", false, "actionConfirmationRequired", true));
        OutcomeGuardrailPolicy policy = policyRepository.save(OutcomeGuardrailPolicy.builder()
                .policyId(policyId).guardrailId(guardrailId).scenarioId(draft.getScenarioId())
                .resultId(draft.getResultId()).userId(userId)
                .fundingAccountId(request.fundingAccountId()).protectedAccountId(request.protectedAccountId())
                .currency(draft.getCurrency()).triggerThreshold(money(scenarioVersion.getProtectedMinimum()))
                .maxActionAmount(money(request.maxActionAmount())).totalLimit(money(request.totalLimit()))
                .totalExecuted(money(BigDecimal.ZERO)).totalReserved(money(BigDecimal.ZERO))
                .maxExecutions(request.maxExecutions()).executionCount(0)
                .termsVersion(request.termsVersion()).termsHash(request.termsHash())
                .consentEvidenceJson(evidence).consentIdempotencyKey(key)
                .consentRequestFingerprint(requestFingerprint).activationChallengeId(challenge.challengeId())
                .activationChallengeExpiresAt(challenge.expiresAt()).activationFingerprint(activationFingerprint)
                .status("CONSENT_PENDING").expiresAt(request.expiresAt()).consentedAt(now).build());
        OutcomeDomainEvent event = recordEvent("GUARDRAIL_CONSENT_RECORDED", policy,
                "guardrail-consent:" + guardrailId, Map.of(
                        "termsVersion", policy.getTermsVersion(), "termsHash", policy.getTermsHash(),
                        "fundingAccountId", policy.getFundingAccountId(),
                        "protectedAccountId", policy.getProtectedAccountId(),
                        "maxActionAmount", policy.getMaxActionAmount(), "totalLimit", policy.getTotalLimit(),
                        "maxExecutions", policy.getMaxExecutions(), "expiresAt", policy.getExpiresAt().toString()));
        var delivery = notify(event, policy, "BALANCE_SHIELD_CONSENT_PENDING", "INFO",
                "Balance Shield activation requires verification",
                "Your consent was recorded. Complete MFA verification to activate; no money has moved.");
        return response(policy, notificationDeliveryService.evidence(delivery));
    }

    @Transactional
    public GuardrailPolicyResponse activate(String guardrailId, GuardrailActivationRequest request, String userId) {
        OutcomeGuardrailPolicy policy = lockedPolicy(guardrailId, userId);
        if ("ACTIVE".equals(policy.getStatus())) return response(policy);
        requireStatus(policy, "CONSENT_PENDING", "Guardrail is not awaiting activation");
        ensureNotExpired(policy);
        ensureCurrentTerms(policy);
        accountServiceClient.consumeStepUpChallenge(policy.getActivationChallengeId(),
                new StepUpClientDtos.ConsumeChallengeRequest(userId, policy.getActivationFingerprint(),
                        policy.getPolicyId(), request.proof()));
        policy.setStatus("ACTIVE");
        policy.setActivatedAt(Instant.now());
        policy.setSuspendedAt(null);
        policy.setSuspensionReason(null);
        policyRepository.save(policy);
        OutcomeDomainEvent event = recordEvent("GUARDRAIL_ACTIVATED", policy,
                "guardrail-activated:" + guardrailId, Map.of(
                        "termsVersion", policy.getTermsVersion(), "mfa", true,
                        "executionRequiresCustomerConfirmation", true));
        var delivery = notify(event, policy, "BALANCE_SHIELD_ACTIVATED", "SUCCESS",
                "Balance Shield guardrail activated",
                "Your bounded top-up guardrail is active. Activation did not move money.");
        return response(policy, notificationDeliveryService.evidence(delivery));
    }

    @Transactional
    public GuardrailPolicyResponse suspend(String guardrailId, GuardrailLifecycleRequest request,
                                           String userId, String idempotencyKey) {
        OutcomeGuardrailPolicy policy = lockedPolicy(guardrailId, userId);
        if ("SUSPENDED".equals(policy.getStatus())) return response(policy);
        requireStatus(policy, "ACTIVE", "Only an active guardrail can be suspended");
        policy.setStatus("SUSPENDED");
        policy.setSuspendedAt(Instant.now());
        policy.setSuspensionReason(request.reason().trim());
        policyRepository.save(policy);
        OutcomeDomainEvent event = recordEvent("GUARDRAIL_SUSPENDED", policy,
                "guardrail-suspended:" + guardrailId + ":" + requireIdempotencyKey(idempotencyKey),
                Map.of("reason", policy.getSuspensionReason(), "actor", "CUSTOMER"));
        var delivery = notify(event, policy, "BALANCE_SHIELD_SUSPENDED", "WARNING",
                "Balance Shield guardrail suspended", "Future guardrail actions are suspended.");
        return response(policy, notificationDeliveryService.evidence(delivery));
    }

    @Transactional
    public GuardrailPolicyResponse resume(String guardrailId, GuardrailLifecycleRequest request,
                                          String userId, String idempotencyKey) {
        OutcomeGuardrailPolicy policy = lockedPolicy(guardrailId, userId);
        if ("ACTIVE".equals(policy.getStatus())) return response(policy);
        requireStatus(policy, "SUSPENDED", "Only a suspended guardrail can be resumed");
        ensureNotExpired(policy);
        ensureCurrentTerms(policy);
        policy.setStatus("ACTIVE");
        policy.setSuspendedAt(null);
        policy.setSuspensionReason(null);
        policyRepository.save(policy);
        OutcomeDomainEvent event = recordEvent("GUARDRAIL_RESUMED", policy,
                "guardrail-resumed:" + guardrailId + ":" + requireIdempotencyKey(idempotencyKey),
                Map.of("reason", request.reason().trim(), "actor", "CUSTOMER"));
        var delivery = notify(event, policy, "BALANCE_SHIELD_RESUMED", "INFO",
                "Balance Shield guardrail resumed", "Your guardrail may accept explicit actions again.");
        return response(policy, notificationDeliveryService.evidence(delivery));
    }

    @Transactional
    public GuardrailPolicyResponse revoke(String guardrailId, GuardrailLifecycleRequest request,
                                          String userId, String idempotencyKey) {
        OutcomeGuardrailPolicy policy = lockedPolicy(guardrailId, userId);
        if ("REVOKED".equals(policy.getStatus())) return response(policy);
        if ("EXPIRED".equals(policy.getStatus())) throw new IllegalStateException("Expired guardrail cannot be revoked");
        for (OutcomeGuardrailExecution execution : executionRepository
                .findByPolicyIdAndStatus(policy.getPolicyId(), "AWAITING_AUTHORIZATION")) {
            transferAuthorizationService.cancel(execution.getTransferAuthorizationId(), userId);
            execution.setStatus("CANCELLED");
            execution.setLastError("Cancelled by guardrail revocation");
            executionRepository.save(execution);
            policy.setTotalReserved(money(policy.getTotalReserved().subtract(execution.getAmount())));
            recordEvent("GUARDRAIL_ACTION_CANCELLED", policy,
                    "guardrail-action-cancelled:" + execution.getExecutionId(),
                    Map.of("executionId", execution.getExecutionId(), "reason", "GUARDRAIL_REVOKED"));
        }
        policy.setStatus("REVOKED");
        policy.setRevokedAt(Instant.now());
        policy.setRevocationReason(request.reason().trim());
        policyRepository.save(policy);
        OutcomeDomainEvent event = recordEvent("GUARDRAIL_REVOKED", policy,
                "guardrail-revoked:" + guardrailId + ":" + requireIdempotencyKey(idempotencyKey),
                Map.of("reason", policy.getRevocationReason(), "actor", "CUSTOMER",
                        "completedTransfersRemainFinal", true));
        var delivery = notify(event, policy, "BALANCE_SHIELD_REVOKED", "WARNING",
                "Balance Shield guardrail revoked",
                "Future actions are blocked. Completed transfers were not reversed.");
        return response(policy, notificationDeliveryService.evidence(delivery));
    }

    @Transactional(noRollbackFor = Exception.class)
    public GuardrailExecutionResponse execute(String guardrailId, GuardrailExecutionRequest request,
                                              String userId, String idempotencyKey) {
        if (request == null || !request.confirmed()) {
            throw new IllegalArgumentException("Explicit confirmation is required for every guardrail action");
        }
        String key = requireIdempotencyKey(idempotencyKey);
        BigDecimal amount = money(request.amount());
        String requestFingerprint = fingerprint(Map.of(
                "guardrailId", guardrailId, "amount", amount, "confirmed", true));
        var replay = executionRepository.findByUserIdAndIdempotencyKey(userId, key);
        if (replay.isPresent()) {
            requireSame(replay.get().getRequestFingerprint(), requestFingerprint,
                    "Idempotency-Key was already used for a different guardrail action");
            return executionResponse(replay.get(), null);
        }

        OutcomeGuardrailPolicy policy = lockedPolicy(guardrailId, userId);
        replay = executionRepository.findByUserIdAndIdempotencyKey(userId, key);
        if (replay.isPresent()) {
            requireSame(replay.get().getRequestFingerprint(), requestFingerprint,
                    "Idempotency-Key was already used for a different guardrail action");
            return executionResponse(replay.get(), null);
        }
        ensureExecutable(policy);
        validateExecution(policy, amount, userId);

        String executionId = UUID.randomUUID().toString();
        String transferKey = "guardrail:" + executionId;
        OutcomeGuardrailExecution execution = executionRepository.save(OutcomeGuardrailExecution.builder()
                .executionId(executionId).policyId(policy.getPolicyId()).guardrailId(guardrailId)
                .userId(userId).amount(amount).currency(policy.getCurrency()).idempotencyKey(key)
                .requestFingerprint(requestFingerprint).transferIdempotencyKey(transferKey)
                .status("REQUESTED").build());
        try {
            TransactionResponse transfer = transferAuthorizationService.submit(TransferRequest.builder()
                    .fromAccountId(policy.getFundingAccountId()).toAccountId(policy.getProtectedAccountId())
                    .amount(amount).currency(policy.getCurrency())
                    .description("Balance Shield customer-confirmed top-up")
                    .reference("guardrail:" + guardrailId).build(), userId, transferKey);
            if (Boolean.TRUE.equals(transfer.getAuthorizationRequired())) {
                execution.setStatus("AWAITING_AUTHORIZATION");
                execution.setTransferAuthorizationId(transfer.getTransactionId());
                execution.setAuthorizationChallengeId(transfer.getAuthorizationChallengeId());
                execution.setAuthorizationExpiresAt(transfer.getAuthorizationExpiresAt());
                policy.setTotalReserved(money(policy.getTotalReserved().add(amount)));
                policyRepository.save(policy);
                executionRepository.save(execution);
                OutcomeDomainEvent event = recordEvent("GUARDRAIL_ACTION_AWAITING_AUTHORIZATION", policy,
                        "guardrail-action-awaiting:" + executionId, Map.of(
                                "executionId", executionId, "amount", amount,
                                "riskReasons", transfer.getAuthorizationReasons(), "moneyMoved", false));
                var delivery = notify(event, policy, "BALANCE_SHIELD_ACTION_REQUIRES_MFA", "WARNING",
                        "Balance Shield top-up requires verification",
                        "Your requested top-up is pending risk-based MFA. No money has moved.");
                return executionResponse(execution, notificationDeliveryService.evidence(delivery));
            }
            if (transfer.getStatus() != TransactionStatus.COMPLETED) {
                throw new IllegalStateException("Authorized transfer did not complete");
            }
            completeExecution(policy, execution, transfer.getTransactionId());
            OutcomeDomainEvent event = recordEvent("GUARDRAIL_ACTION_COMPLETED", policy,
                    "guardrail-action-completed:" + executionId, Map.of(
                            "executionId", executionId, "transactionId", transfer.getTransactionId(),
                            "amount", amount, "currency", policy.getCurrency()));
            var delivery = notify(event, policy, "BALANCE_SHIELD_ACTION_COMPLETED", "SUCCESS",
                    "Balance Shield top-up completed",
                    "Your explicitly confirmed top-up completed through the authorized transfer flow.");
            return executionResponse(execution, notificationDeliveryService.evidence(delivery));
        } catch (RuntimeException failure) {
            if ("COMPLETED".equals(execution.getStatus()) || "AWAITING_AUTHORIZATION".equals(execution.getStatus())) {
                execution.setLastError("Transfer state preserved; notification evidence enqueue failed: " + sanitize(failure));
                executionRepository.save(execution);
                return executionResponse(execution, null);
            }
            execution.setStatus("FAILED");
            execution.setLastError(sanitize(failure));
            executionRepository.save(execution);
            OutcomeDomainEvent event = recordEvent("GUARDRAIL_ACTION_FAILED", policy,
                    "guardrail-action-failed:" + executionId, Map.of(
                            "executionId", executionId, "reason", execution.getLastError(), "moneyMoved", false));
            notify(event, policy, "BALANCE_SHIELD_ACTION_FAILED", "ERROR",
                    "Balance Shield top-up failed", "The requested top-up did not complete. Review the failure evidence.");
            throw failure;
        }
    }

    @Transactional(noRollbackFor = Exception.class)
    public GuardrailExecutionResponse authorize(String executionId, GuardrailExecutionAuthorizationRequest request,
                                                String userId, String idempotencyKey) {
        OutcomeGuardrailExecution found = executionRepository.findByExecutionIdAndUserId(executionId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail execution not found"));
        OutcomeGuardrailPolicy policy = policyRepository.lockByPolicyAndUser(found.getPolicyId(), userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail execution not found"));
        OutcomeGuardrailExecution execution = executionRepository.lockByExecutionAndUser(executionId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail execution not found"));
        if ("COMPLETED".equals(execution.getStatus())) return executionResponse(execution, null);
        requireStatus(execution, "AWAITING_AUTHORIZATION", "Guardrail action is not awaiting authorization");
        ensureExecutable(policy);
        String key = requireIdempotencyKey(idempotencyKey);
        try {
            TransactionResponse transfer = transferAuthorizationService.authorize(
                    execution.getTransferAuthorizationId(), userId, request.proof());
            if (transfer.getStatus() != TransactionStatus.COMPLETED) {
                throw new IllegalStateException("Authorized transfer did not complete");
            }
            policy.setTotalReserved(money(policy.getTotalReserved().subtract(execution.getAmount())));
            completeExecution(policy, execution, transfer.getTransactionId());
            OutcomeDomainEvent event = recordEvent("GUARDRAIL_ACTION_COMPLETED", policy,
                    "guardrail-action-completed:" + executionId, Map.of(
                            "executionId", executionId, "transactionId", transfer.getTransactionId(),
                            "amount", execution.getAmount(), "riskStepUp", true));
            var delivery = notify(event, policy, "BALANCE_SHIELD_ACTION_COMPLETED", "SUCCESS",
                    "Balance Shield top-up completed",
                    "Your verified top-up completed through the authorized transfer flow.");
            return executionResponse(execution, notificationDeliveryService.evidence(delivery));
        } catch (RuntimeException failure) {
            if ("COMPLETED".equals(execution.getStatus())) {
                execution.setLastError("Transfer completed; notification evidence enqueue failed: " + sanitize(failure));
                executionRepository.save(execution);
                return executionResponse(execution, null);
            }
            execution.setLastError(sanitize(failure));
            executionRepository.save(execution);
            recordEvent("GUARDRAIL_ACTION_AUTHORIZATION_FAILED", policy,
                    "guardrail-action-auth-failed:" + executionId + ":" + key,
                    Map.of("executionId", executionId, "reason", execution.getLastError(), "retryable", true));
            throw failure;
        }
    }

    @Transactional
    public GuardrailExecutionResponse cancelExecution(String executionId, String userId, String idempotencyKey) {
        OutcomeGuardrailExecution found = executionRepository.findByExecutionIdAndUserId(executionId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail execution not found"));
        OutcomeGuardrailPolicy policy = policyRepository.lockByPolicyAndUser(found.getPolicyId(), userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail execution not found"));
        OutcomeGuardrailExecution execution = executionRepository.lockByExecutionAndUser(executionId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail execution not found"));
        if ("CANCELLED".equals(execution.getStatus())) return executionResponse(execution, null);
        if ("COMPLETED".equals(execution.getStatus())) {
            throw new IllegalStateException("Completed guardrail action cannot be cancelled");
        }
        if ("AWAITING_AUTHORIZATION".equals(execution.getStatus())) {
            transferAuthorizationService.cancel(execution.getTransferAuthorizationId(), userId);
            policy.setTotalReserved(money(policy.getTotalReserved().subtract(execution.getAmount())));
            policyRepository.save(policy);
        }
        execution.setStatus("CANCELLED");
        execution.setLastError("Cancelled by customer");
        executionRepository.save(execution);
        recordEvent("GUARDRAIL_ACTION_CANCELLED", policy,
                "guardrail-action-cancelled:" + executionId + ":" + requireIdempotencyKey(idempotencyKey),
                Map.of("executionId", executionId, "actor", "CUSTOMER"));
        return executionResponse(execution, null);
    }

    @Transactional(readOnly = true)
    public GuardrailPolicyResponse policy(String guardrailId, String userId) {
        return response(policyRepository.findByGuardrailIdAndUserId(guardrailId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail policy not found")));
    }

    @Transactional(readOnly = true)
    public List<GuardrailAuditEventResponse> events(String guardrailId, String userId) {
        draftRepository.findByGuardrailIdAndUserId(guardrailId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail not found"));
        return eventRepository.findByGuardrailIdAndUserIdOrderByCreatedAtAsc(guardrailId, userId).stream()
                .map(event -> new GuardrailAuditEventResponse(event.getEventId(), event.getEventType(),
                        event.getGuardrailId(), event.getFieldsJson(), event.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GuardrailOperatorPolicyResponse> operatorPolicies() {
        return policyRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(policy -> new GuardrailOperatorPolicyResponse(policy.getUserId(), policy.getScenarioId(), response(policy)))
                .toList();
    }

    @Transactional(readOnly = true)
    public GuardrailPolicyResponse optionalPolicy(String guardrailId, String userId) {
        return policyRepository.findByGuardrailIdAndUserId(guardrailId, userId).map(this::response).orElse(null);
    }

    private void completeExecution(OutcomeGuardrailPolicy policy, OutcomeGuardrailExecution execution, String transactionId) {
        execution.setStatus("COMPLETED");
        execution.setTransactionId(transactionId);
        execution.setCompletedAt(Instant.now());
        execution.setLastError(null);
        policy.setTotalExecuted(money(policy.getTotalExecuted().add(execution.getAmount())));
        policy.setExecutionCount(policy.getExecutionCount() + 1);
        policyRepository.save(policy);
        executionRepository.save(execution);
    }

    private void validateExecution(OutcomeGuardrailPolicy policy, BigDecimal amount, String userId) {
        if (amount.compareTo(policy.getMaxActionAmount()) > 0) {
            throw new IllegalArgumentException("Requested amount exceeds the per-action limit");
        }
        BigDecimal remaining = policy.getTotalLimit().subtract(policy.getTotalExecuted()).subtract(policy.getTotalReserved());
        if (amount.compareTo(remaining) > 0) throw new IllegalArgumentException("Requested amount exceeds the remaining total limit");
        long pending = executionRepository.findByPolicyIdAndStatus(policy.getPolicyId(), "AWAITING_AUTHORIZATION").size();
        if (policy.getExecutionCount() + pending >= policy.getMaxExecutions()) {
            throw new IllegalArgumentException("Guardrail execution-count limit is exhausted");
        }
        LedgerAccount funding = ownedAccount(policy.getFundingAccountId(), userId, policy.getCurrency());
        LedgerAccount protectedAccount = ownedAccount(policy.getProtectedAccountId(), userId, policy.getCurrency());
        LedgerBalanceProjection fundingBalance = projection(funding);
        LedgerBalanceProjection protectedBalance = projection(protectedAccount);
        if (protectedBalance.getAvailableBalance().compareTo(policy.getTriggerThreshold()) >= 0) {
            throw new IllegalStateException("Protected account is already at or above the guardrail threshold");
        }
        BigDecimal deficit = money(policy.getTriggerThreshold().subtract(protectedBalance.getAvailableBalance()));
        if (amount.compareTo(deficit) > 0) {
            throw new IllegalArgumentException("Requested amount exceeds the current threshold deficit");
        }
        if (fundingBalance.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Funding account does not have enough authoritative available balance");
        }
    }

    private void validateExecutableDraft(OutcomeGuardrailDraft draft) {
        if (!"RESERVE_BUFFER".equals(draft.getGuardrailType())) {
            throw new IllegalArgumentException("Only a reserve-buffer draft can become an executable top-up guardrail");
        }
        if (!draft.getExpiresAt().isAfter(Instant.now())) throw new IllegalStateException("Guardrail draft has expired");
    }

    private void validateConsentLimits(OutcomeGuardrailDraft draft, GuardrailConsentRequest request) {
        BigDecimal max = money(request.maxActionAmount());
        BigDecimal total = money(request.totalLimit());
        if (max.signum() <= 0 || total.signum() <= 0 || max.compareTo(total) > 0) {
            throw new IllegalArgumentException("Guardrail limits are invalid");
        }
        if (max.compareTo(money(draft.getThresholdAmount())) > 0 || total.compareTo(money(draft.getThresholdAmount())) > 0) {
            throw new IllegalArgumentException("Executable limits cannot exceed the compiled reserve-buffer amount");
        }
        Instant now = Instant.now();
        if (!request.expiresAt().isAfter(now) || request.expiresAt().isAfter(draft.getExpiresAt())) {
            throw new IllegalArgumentException("Guardrail expiry must be in the future and within the draft horizon");
        }
    }

    private void validateConsentAccounts(OutcomeGuardrailDraft draft, GuardrailConsentRequest request, String userId) {
        if (request.fundingAccountId().equals(request.protectedAccountId())) {
            throw new IllegalArgumentException("Funding and protected accounts must be different");
        }
        List<String> scope = read(draft.getScopeJson(), STRING_LIST);
        if (!scope.contains(request.protectedAccountId())) {
            throw new IllegalArgumentException("Protected account must be inside the compiled guardrail scope");
        }
        if (scope.contains(request.fundingAccountId())) {
            throw new IllegalArgumentException("Funding account must be outside the protected scenario scope");
        }
        ownedAccount(request.fundingAccountId(), userId, draft.getCurrency());
        ownedAccount(request.protectedAccountId(), userId, draft.getCurrency());
    }

    private LedgerAccount ownedAccount(String externalAccountId, String userId, String currency) {
        LedgerAccount account = ledgerAccountRepository.findByExternalAccountId(externalAccountId)
                .filter(candidate -> candidate.getAccountKind() == LedgerAccountKind.CUSTOMER)
                .orElseThrow(() -> new AccessDeniedException("Guardrail account not found"));
        if (!userId.equals(account.getOwnerId())) throw new AccessDeniedException("Guardrail account not found");
        if (!currency.equals(account.getCurrency())) throw new IllegalArgumentException("Guardrail accounts must use the consent currency");
        return account;
    }

    private LedgerBalanceProjection projection(LedgerAccount account) {
        return projectionRepository.findById(account.getLedgerAccountId())
                .orElseThrow(() -> new IllegalStateException("Authoritative balance projection is missing"));
    }

    private void ensureExecutable(OutcomeGuardrailPolicy policy) {
        requireStatus(policy, "ACTIVE", "Guardrail is not active");
        ensureNotExpired(policy);
        ensureCurrentTerms(policy);
        OutcomeGuardrailRuntimeControl control = controlRepository.findById(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)
                .orElseThrow(() -> new IllegalStateException("Guardrail execution control is not configured"));
        if (!control.isExecutionEnabled()) {
            throw new IllegalStateException("Guardrail execution is suspended by the emergency kill switch");
        }
    }

    private void ensureNotExpired(OutcomeGuardrailPolicy policy) {
        if (!policy.getExpiresAt().isAfter(Instant.now())) {
            policy.setStatus("EXPIRED");
            policyRepository.save(policy);
            throw new IllegalStateException("Guardrail has expired");
        }
    }

    private void ensureCurrentTerms(OutcomeGuardrailPolicy policy) {
        if (!termsVersion.equals(policy.getTermsVersion()) || !termsHash().equals(policy.getTermsHash())) {
            throw new IllegalStateException("Guardrail terms changed; new informed consent is required");
        }
    }

    private void validateTerms(String version, String hash) {
        if (!termsVersion.equals(version) || !termsHash().equals(hash)) {
            throw new IllegalArgumentException("Current guardrail terms must be reviewed before consent");
        }
    }

    private OutcomeGuardrailPolicy lockedPolicy(String guardrailId, String userId) {
        return policyRepository.lockByGuardrailAndUser(guardrailId, userId)
                .orElseThrow(() -> new AccessDeniedException("Guardrail policy not found"));
    }

    private void requireStatus(OutcomeGuardrailPolicy policy, String expected, String message) {
        if (!expected.equals(policy.getStatus())) throw new IllegalStateException(message);
    }

    private void requireStatus(OutcomeGuardrailExecution execution, String expected, String message) {
        if (!expected.equals(execution.getStatus())) throw new IllegalStateException(message);
    }

    private GuardrailPolicyResponse response(OutcomeGuardrailPolicy policy) {
        return response(policy, notificationDeliveryService.latestEvidence(policy.getUserId(), policy.getScenarioId()).orElse(null));
    }

    private GuardrailPolicyResponse response(OutcomeGuardrailPolicy policy, NotificationDeliveryEvidence delivery) {
        OutcomeGuardrailRuntimeControl control = controlRepository.findById(OutcomeGuardrailControlService.GLOBAL_CONTROL_ID)
                .orElseThrow(() -> new IllegalStateException("Guardrail execution control is not configured"));
        boolean requiresReconsent = !termsVersion.equals(policy.getTermsVersion()) || !termsHash().equals(policy.getTermsHash());
        String effective = policy.getStatus();
        if (policy.getExpiresAt().isBefore(Instant.now()) && !"REVOKED".equals(effective)) effective = "EXPIRED";
        else if ("ACTIVE".equals(effective) && (!control.isExecutionEnabled() || requiresReconsent)) effective = "SUSPENDED";
        return new GuardrailPolicyResponse(policy.getPolicyId(), policy.getGuardrailId(),
                policy.getFundingAccountId(), policy.getProtectedAccountId(), policy.getCurrency(),
                policy.getTriggerThreshold(), policy.getMaxActionAmount(), policy.getTotalLimit(),
                policy.getTotalExecuted(), policy.getTotalReserved(), policy.getMaxExecutions(),
                policy.getExecutionCount(), policy.getTermsVersion(), policy.getTermsHash(), policy.getStatus(),
                effective, policy.getExpiresAt(), policy.getConsentedAt(), policy.getActivatedAt(),
                policy.getSuspendedAt(), policy.getSuspensionReason(), policy.getRevokedAt(),
                policy.getRevocationReason(), policy.getActivationChallengeId(),
                policy.getActivationChallengeExpiresAt(), control.isExecutionEnabled(), control.getReason(),
                requiresReconsent, delivery);
    }

    private GuardrailExecutionResponse executionResponse(OutcomeGuardrailExecution execution,
                                                         NotificationDeliveryEvidence delivery) {
        return new GuardrailExecutionResponse(execution.getExecutionId(), execution.getGuardrailId(),
                execution.getPolicyId(), execution.getAmount(), execution.getCurrency(), execution.getStatus(),
                execution.getTransactionId(), "AWAITING_AUTHORIZATION".equals(execution.getStatus()),
                execution.getAuthorizationChallengeId(), execution.getAuthorizationExpiresAt(),
                execution.getLastError(), execution.getCreatedAt(), execution.getCompletedAt(), delivery);
    }

    private OutcomeDomainEvent recordEvent(String type, OutcomeGuardrailPolicy policy,
                                           String dedupeKey, Map<String, ?> fields) {
        var existing = eventRepository.findByUserIdAndDedupeKey(policy.getUserId(), dedupeKey);
        if (existing.isPresent()) return existing.get();
        OutcomeScenario scenario = scenarioRepository.findById(policy.getScenarioId()).orElseThrow();
        return eventRepository.save(OutcomeDomainEvent.builder()
                .eventId(UUID.randomUUID().toString()).eventType(type).userId(policy.getUserId())
                .scenarioId(policy.getScenarioId()).scenarioVersion(scenario.getCurrentVersion())
                .resultId(policy.getResultId()).guardrailId(policy.getGuardrailId())
                .dedupeKey(dedupeKey).fieldsJson(json(fields)).build());
    }

    private OutcomeNotificationDelivery notify(OutcomeDomainEvent event, OutcomeGuardrailPolicy policy,
                                                String type, String severity, String title, String message) {
        OutcomeScenario scenario = scenarioRepository.findById(policy.getScenarioId()).orElseThrow();
        return notificationDeliveryService.enqueueEvent(event, scenario, type, severity, title, message,
                "outcome-guardrail:" + event.getEventId());
    }

    private Map<String, ?> consentFingerprint(OutcomeGuardrailDraft draft, GuardrailConsentRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("guardrailId", draft.getGuardrailId());
        values.put("termsVersion", request.termsVersion());
        values.put("termsHash", request.termsHash());
        values.put("fundingAccountId", request.fundingAccountId());
        values.put("protectedAccountId", request.protectedAccountId());
        values.put("maxActionAmount", money(request.maxActionAmount()));
        values.put("totalLimit", money(request.totalLimit()));
        values.put("maxExecutions", request.maxExecutions());
        values.put("expiresAt", request.expiresAt().toString());
        return values;
    }

    private String termsHash() { return fingerprint(TERMS_TEXT); }

    private String fingerprint(Object value) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint guardrail evidence", e);
        }
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        String key = value.trim();
        if (key.length() > 128) throw new IllegalArgumentException("Idempotency-Key is too long");
        return key;
    }

    private void requireSame(String existing, String requested, String message) {
        if (!Objects.equals(existing, requested)) throw new IllegalStateException(message);
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_EVEN); }

    private String sanitize(RuntimeException failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize guardrail evidence", e); }
    }

    private <T> T read(String value, TypeReference<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unable to read guardrail scope", e); }
    }
}
