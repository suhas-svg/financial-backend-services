package com.suhasan.finance.transaction_service.sandbox;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.service.SyntheticFundingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SyntheticSandboxSeedServiceTest {
    private SyntheticSandboxGuard guard;
    private SyntheticSandboxAccountClient sandboxClient;
    private ResilientAccountServiceClient accountClient;
    private SyntheticFundingService fundingService;
    private SyntheticSandboxSeedService service;

    @BeforeEach
    void setUp() {
        guard = mock(SyntheticSandboxGuard.class);
        sandboxClient = mock(SyntheticSandboxAccountClient.class);
        accountClient = mock(ResilientAccountServiceClient.class);
        fundingService = mock(SyntheticFundingService.class);
        service = new SyntheticSandboxSeedService(guard, sandboxClient, accountClient, fundingService);
        ReflectionTestUtils.setField(service, "fundedAmount", new BigDecimal("1000.00"));
    }

    @Test
    void bindsMfaChallengeAndFundingToSameIdempotencyKey() {
        when(accountClient.createStepUpChallenge(any())).thenReturn(
                new StepUpClientDtos.CreateChallengeResponse("challenge-1", Instant.now().plusSeconds(120)));
        var challenge = service.challenge("operator", "seed-run-1");
        assertThat(challenge.challengeId()).isEqualTo("challenge-1");

        when(sandboxClient.seedAccounts("operator")).thenReturn(new SyntheticSandboxAccountClient.SeededAccounts(
                "controlled-beta-phase2-v1", "101", "102", List.of("101", "102")));
        when(fundingService.fund(eq("102"), eq(new BigDecimal("1000.00")), anyString(),
                eq("sandbox-seed:seed-run-1"), eq("operator"))).thenReturn(TransactionResponse.builder()
                .transactionId("txn-1").idempotencyKey("sandbox-seed:seed-run-1").build());

        var result = service.seed("operator", "seed-run-1", "challenge-1", "one-time-proof");
        assertThat(result.zeroAccountId()).isEqualTo("101");
        assertThat(result.fundedAccountId()).isEqualTo("102");
        verify(accountClient).consumeStepUpChallenge(eq("challenge-1"), argThat(request ->
                request.userId().equals("operator") && request.consumerKey().equals("sandbox-seed:seed-run-1")
                        && request.proof().equals("one-time-proof")));
    }

    @Test
    void rejectsMissingIdempotencyBeforeChallengeCreation() {
        assertThatThrownBy(() -> service.challenge("operator", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Idempotency-Key");
        verifyNoInteractions(accountClient);
    }
}
