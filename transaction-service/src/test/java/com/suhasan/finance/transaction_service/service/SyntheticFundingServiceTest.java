package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyntheticFundingServiceTest {
    private TransactionService transactionService;
    private ResilientAccountServiceClient accountServiceClient;
    private AuditService auditService;
    private Environment environment;
    private SyntheticFundingService service;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        accountServiceClient = mock(ResilientAccountServiceClient.class);
        auditService = mock(AuditService.class);
        environment = mock(Environment.class);
        service = new SyntheticFundingService(transactionService, accountServiceClient, auditService, environment);
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    void postsSyntheticFundingThroughLedgerAuthoritativeDepositWithProvenance() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        when(accountServiceClient.getAccountInternal("42")).thenReturn(AccountDto.builder()
                .id(42L).ownerId("customer-1").status("ACTIVE").build());
        when(transactionService.processDeposit(
                "42", new BigDecimal("25.00"), "[SYNTHETIC] beta seed",
                "SYNTHETIC:fund-1", "customer-1", "synthetic:fund-1"))
                .thenReturn(TransactionResponse.builder().transactionId("txn-1").build());

        TransactionResponse result = service.fund(
                "42", new BigDecimal("25.00"), "beta seed", "fund-1", "operator-1");

        assertThat(result.getTransactionId()).isEqualTo("txn-1");
        verify(transactionService).processDeposit(
                "42", new BigDecimal("25.00"), "[SYNTHETIC] beta seed",
                "SYNTHETIC:fund-1", "customer-1", "synthetic:fund-1");
        verify(auditService).logSecurityEvent(
                "SYNTHETIC_FUNDING", "operator-1", "transaction=txn-1 account=42",
                "operator-authenticated");
    }

    @Test
    void productionProfileFailsClosedEvenWhenFlagIsEnabled() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        assertThatThrownBy(() -> service.fund(
                "42", BigDecimal.ONE, "seed", "fund-1", "operator-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void idempotencyKeyIsMandatory() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        assertThatThrownBy(() -> service.fund(
                "42", BigDecimal.ONE, "seed", " ", "operator-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key");
    }
}
