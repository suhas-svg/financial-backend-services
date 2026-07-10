package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.StepUpInternalDtos;
import com.suhasan.finance.account_service.entity.MfaMethod;
import com.suhasan.finance.account_service.entity.StepUpChallenge;
import com.suhasan.finance.account_service.entity.StepUpChallengeStatus;
import com.suhasan.finance.account_service.exception.MfaVerificationException;
import com.suhasan.finance.account_service.repository.StepUpChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepUpChallengeServiceTest {
    @Mock StepUpChallengeRepository repository;
    @Mock MfaService mfaService;
    private StepUpChallengeService service;

    @BeforeEach
    void setUp() {
        service = new StepUpChallengeService(repository, mfaService);
        ReflectionTestUtils.setField(service, "challengeTtlSeconds", 300L);
        ReflectionTestUtils.setField(service, "proofTtlSeconds", 120L);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        when(repository.save(any(StepUpChallenge.class))).thenAnswer(invocation -> {
            StepUpChallenge value = invocation.getArgument(0);
            if (value.getChallengeId() == null) value.setChallengeId("challenge-1");
            return value;
        });
    }

    @Test
    void proofIsActionBoundSingleUseAndIdempotentForSameConsumer() {
        MfaMethod method = new MfaMethod();
        when(mfaService.activeMethod("alice")).thenReturn(method);
        when(mfaService.verifyCredential(method, "123456")).thenReturn(true);

        service.create(new StepUpInternalDtos.CreateChallengeRequest("alice", "TRANSFER", "fingerprint"));
        StepUpChallenge challenge = new StepUpChallenge();
        challenge.setChallengeId("challenge-1");
        challenge.setUserId("alice");
        challenge.setActionType("TRANSFER");
        challenge.setActionFingerprint("fingerprint");
        challenge.setStatus(StepUpChallengeStatus.PENDING);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        when(repository.findForUpdateByChallengeId("challenge-1")).thenReturn(Optional.of(challenge));

        var verified = service.verify("challenge-1", "alice", "123456");
        assertThat(verified.proof()).isNotBlank();
        assertThat(challenge.getProofHash()).doesNotContain(verified.proof());

        var request = new StepUpInternalDtos.ConsumeChallengeRequest(
                "alice", "fingerprint", "authorization-1", verified.proof());
        assertThat(service.consume("challenge-1", request).consumed()).isTrue();
        assertThat(service.consume("challenge-1", request).consumed()).isTrue();
        assertThatThrownBy(() -> service.consume("challenge-1",
                new StepUpInternalDtos.ConsumeChallengeRequest(
                        "alice", "fingerprint", "authorization-2", verified.proof())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidCredentialsLockChallengeAtConfiguredAttemptLimit() {
        MfaMethod method = new MfaMethod();
        when(mfaService.activeMethod("alice")).thenReturn(method);
        when(mfaService.verifyCredential(method, "bad-code")).thenReturn(false);
        StepUpChallenge challenge = new StepUpChallenge();
        challenge.setChallengeId("challenge-1");
        challenge.setUserId("alice");
        challenge.setStatus(StepUpChallengeStatus.PENDING);
        challenge.setExpiresAt(Instant.now().plusSeconds(300));
        challenge.setAttempts(2);
        when(repository.findForUpdateByChallengeId("challenge-1")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.verify("challenge-1", "alice", "bad-code"))
                .isInstanceOf(MfaVerificationException.class);
        assertThat(challenge.getStatus()).isEqualTo(StepUpChallengeStatus.LOCKED);
    }
}
