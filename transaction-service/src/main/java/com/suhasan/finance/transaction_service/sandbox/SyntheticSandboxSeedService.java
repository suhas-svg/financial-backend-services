package com.suhasan.finance.transaction_service.sandbox;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.service.SyntheticFundingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class SyntheticSandboxSeedService {
    private static final String SEED_VERSION = "controlled-beta-phase2-v1";
    private final SyntheticSandboxGuard guard;
    private final SyntheticSandboxAccountClient sandboxAccountClient;
    private final ResilientAccountServiceClient accountServiceClient;
    private final SyntheticFundingService syntheticFundingService;

    @Value("${sandbox.seed.funded-amount:1000.00}")
    private BigDecimal fundedAmount;

    public StepUpClientDtos.CreateChallengeResponse challenge(String operator, String idempotencyKey) {
        guard.requireSynthetic();
        return accountServiceClient.createStepUpChallenge(new StepUpClientDtos.CreateChallengeRequest(
                operator, "SYNTHETIC_SANDBOX_SEED", fingerprint(operator, idempotencyKey)));
    }

    public SeedResult seed(String operator, String idempotencyKey, String challengeId, String proof) {
        guard.requireSynthetic();
        String fingerprint = fingerprint(operator, idempotencyKey);
        accountServiceClient.consumeStepUpChallenge(challengeId, new StepUpClientDtos.ConsumeChallengeRequest(
                operator, fingerprint, "sandbox-seed:" + idempotencyKey, proof));
        SyntheticSandboxAccountClient.SeededAccounts accounts = sandboxAccountClient.seedAccounts(operator);
        TransactionResponse funding = syntheticFundingService.fund(accounts.fundedAccountId(), fundedAmount,
                "controlled beta reproducible seed", "sandbox-seed:" + idempotencyKey, operator);
        return new SeedResult(accounts.seedVersion(), accounts.zeroAccountId(), accounts.fundedAccountId(),
                fundedAmount, funding.getTransactionId(), funding.getIdempotencyKey());
    }

    private String fingerprint(String operator, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("A bounded Idempotency-Key is required");
        }
        String canonical = "SYNTHETIC_SANDBOX_SEED|" + SEED_VERSION + "|" + operator + "|" + idempotencyKey.trim();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to fingerprint synthetic seed action", failure);
        }
    }

    public record SeedResult(String seedVersion, String zeroAccountId, String fundedAccountId,
                             BigDecimal fundedAmount, String fundingTransactionId, String fundingIdempotencyKey) {}
}
