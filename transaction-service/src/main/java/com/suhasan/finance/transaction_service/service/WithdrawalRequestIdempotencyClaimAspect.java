package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.WithdrawalRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WithdrawalRequestIdempotencyClaimAspect {
    private final TransactionIdempotencyClaimService claimService;

    @Around("execution(* com.suhasan.finance.transaction_service.controller.TransactionController.processWithdrawal(..)) "
            + "&& args(request,idempotencyKey,authentication)")
    public Object claimBeforeWithdrawal(ProceedingJoinPoint joinPoint, WithdrawalRequest request,
                                        String idempotencyKey, Authentication authentication) throws Throwable {
        claimService.claimWithdrawalRequest(
                request.getAccountId(), request.getAmount(), request.getCurrency(),
                request.getDescription(), request.getReference(),
                authentication.getName(), idempotencyKey);
        return joinPoint.proceed();
    }
}
