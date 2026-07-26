package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.MfaResponses;
import com.suhasan.finance.account_service.dto.StepUpInternalDtos;
import com.suhasan.finance.account_service.entity.MfaMethod;
import com.suhasan.finance.account_service.entity.StepUpChallenge;
import com.suhasan.finance.account_service.entity.StepUpChallengeStatus;
import com.suhasan.finance.account_service.exception.MfaVerificationException;
import com.suhasan.finance.account_service.repository.StepUpChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Transactional
public class StepUpChallengeService {
    private final StepUpChallengeRepository challengeRepository;
    private final MfaService mfaService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.step-up.challenge-ttl-seconds:300}")
    private long challengeTtlSeconds;
    @Value("${security.step-up.proof-ttl-seconds:120}")
    private long proofTtlSeconds;
    @Value("${security.step-up.max-attempts:5}")
    private int maxAttempts;

    public StepUpInternalDtos.CreateChallengeResponse create(final StepUpInternalDtos.CreateChallengeRequest request) {
        mfaService.activeMethod(request.userId());
        final Instant now = Instant.now();
        StepUpChallenge challenge = new StepUpChallenge();
        challenge.setUserId(request.userId());
        challenge.setActionType(request.actionType());
        challenge.setActionFingerprint(request.actionFingerprint());
        challenge.setStatus(StepUpChallengeStatus.PENDING);
        challenge.setExpiresAt(now.plusSeconds(challengeTtlSeconds));
        challenge = challengeRepository.save(challenge);
        return new StepUpInternalDtos.CreateChallengeResponse(challenge.getChallengeId(), challenge.getExpiresAt());
    }

    public MfaResponses.ChallengeVerificationResponse verify(final String challengeId, final String username, final String credential) {
        final StepUpChallenge challenge = locked(challengeId);
        assertOwner(challenge, username);
        expireIfNecessary(challenge);
        if (challenge.getStatus() == StepUpChallengeStatus.LOCKED) {
            throw new IllegalStateException("Challenge is locked");
        }
        if (challenge.getStatus() != StepUpChallengeStatus.PENDING) {
            throw new IllegalStateException("Challenge cannot be verified in its current state");
        }
        final MfaMethod method = mfaService.activeMethod(username);
        if (!mfaService.verifyCredential(method, credential)) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() >= maxAttempts) {
                challenge.setStatus(StepUpChallengeStatus.LOCKED);
            }
            challengeRepository.save(challenge);
            throw new MfaVerificationException("Invalid verification credential");
        }
        final String proof = randomProof();
        final Instant now = Instant.now();
        challenge.setStatus(StepUpChallengeStatus.VERIFIED);
        challenge.setProofHash(sha256(proof));
        challenge.setVerifiedAt(now);
        challenge.setProofExpiresAt(now.plusSeconds(proofTtlSeconds));
        challengeRepository.save(challenge);
        return new MfaResponses.ChallengeVerificationResponse(challenge.getChallengeId(), proof, challenge.getProofExpiresAt());
    }

    public StepUpInternalDtos.ConsumeChallengeResponse consume(
            final String challengeId, final StepUpInternalDtos.ConsumeChallengeRequest request) {
        final StepUpChallenge challenge = locked(challengeId);
        assertOwner(challenge, request.userId());
        if (!MessageDigest.isEqual(challenge.getActionFingerprint().getBytes(StandardCharsets.UTF_8),
                request.actionFingerprint().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Challenge action does not match");
        }
        if (challenge.getStatus() == StepUpChallengeStatus.CONSUMED) {
            if (request.consumerKey().equals(challenge.getConsumerKey())) {
                return new StepUpInternalDtos.ConsumeChallengeResponse(true, challenge.getConsumedAt());
            }
            throw new IllegalStateException("Challenge has already been consumed");
        }
        if (challenge.getStatus() != StepUpChallengeStatus.VERIFIED) {
            throw new IllegalStateException("Challenge has not been verified");
        }
        if (challenge.getProofExpiresAt() == null || !challenge.getProofExpiresAt().isAfter(Instant.now())) {
            challenge.setStatus(StepUpChallengeStatus.EXPIRED);
            challengeRepository.save(challenge);
            throw new IllegalStateException("Challenge proof has expired");
        }
        if (!MessageDigest.isEqual(challenge.getProofHash().getBytes(StandardCharsets.US_ASCII),
                sha256(request.proof()).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid challenge proof");
        }
        challenge.setStatus(StepUpChallengeStatus.CONSUMED);
        challenge.setConsumerKey(request.consumerKey());
        challenge.setConsumedAt(Instant.now());
        challengeRepository.save(challenge);
        return new StepUpInternalDtos.ConsumeChallengeResponse(true, challenge.getConsumedAt());
    }

    private StepUpChallenge locked(final String challengeId) {
        return challengeRepository.findForUpdateByChallengeId(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));
    }

    private void assertOwner(final StepUpChallenge challenge, final String username) {
        if (!challenge.getUserId().equals(username)) {
            throw new IllegalArgumentException("Challenge not found");
        }
    }

    private void expireIfNecessary(final StepUpChallenge challenge) {
        if (challenge.getStatus() == StepUpChallengeStatus.PENDING && !challenge.getExpiresAt().isAfter(Instant.now())) {
            challenge.setStatus(StepUpChallengeStatus.EXPIRED);
            challengeRepository.save(challenge);
            throw new IllegalStateException("Challenge has expired");
        }
    }

    private String randomProof() {
        final byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(final String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash challenge proof", e);
        }
    }
}
