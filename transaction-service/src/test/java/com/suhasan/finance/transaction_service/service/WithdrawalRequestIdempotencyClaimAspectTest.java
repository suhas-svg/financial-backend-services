package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.WithdrawalRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalRequestIdempotencyClaimAspectTest {
    @Mock TransactionIdempotencyClaimService claimService;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock Authentication authentication;

    private WithdrawalRequestIdempotencyClaimAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new WithdrawalRequestIdempotencyClaimAspect(claimService);
    }

    @Test
    void claimsCurrencyAndFullPayloadBeforeControllerInvokesTransactionService() throws Throwable {
        WithdrawalRequest request = WithdrawalRequest.builder()
                .accountId("7")
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .description("cash")
                .reference("ref")
                .build();
        Object response = new Object();
        when(authentication.getName()).thenReturn("alice");
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.claimBeforeWithdrawal(joinPoint, request, "key-1", authentication);

        assertThat(result).isSameAs(response);
        InOrder order = inOrder(claimService, joinPoint);
        order.verify(claimService).claimWithdrawalRequest(
                "7", new BigDecimal("25.00"), "USD", "cash", "ref", "alice", "key-1");
        order.verify(joinPoint).proceed();
    }
}
