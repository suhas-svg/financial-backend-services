package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.exception.MoneyMovementBusyException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Caps how many deposits, withdrawals and transfers execute at once.
 *
 * <p>Money movement deliberately commits idempotency claims and remote-effect records in
 * their own ({@code REQUIRES_NEW}) transactions, so one movement holds a second database
 * connection while its main transaction is open. Without a cap, enough concurrent movements
 * each hold one connection while waiting for another, the pool runs dry, and every request
 * (reads and health checks included) waits for the connection timeout. The first load drill
 * showed exactly that at 10 transfer pairs per second against a 10-connection pool.
 *
 * <p>The permit is taken outside every other aspect and outside the transaction, so a waiting
 * request holds no connection. Keep {@code max-concurrent x 2 < pool size}. Calls that are
 * already inside a movement on the same thread pass through.
 */
@Aspect
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class MoneyMovementBulkhead {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private final Semaphore permits;
    private final long waitMillis;

    public MoneyMovementBulkhead(
            @Value("${transaction.money-movement.max-concurrent:8}") int maxConcurrent,
            @Value("${transaction.money-movement.max-wait-ms:5000}") long waitMillis,
            MeterRegistry meterRegistry) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("transaction.money-movement.max-concurrent must be at least 1");
        }
        this.permits = new Semaphore(maxConcurrent, true);
        this.waitMillis = Math.max(0, waitMillis);
        Gauge.builder("money.movement.in_flight", permits, p -> maxConcurrent - p.availablePermits())
                .description("Deposits, withdrawals and transfers executing now")
                .register(meterRegistry);
        Gauge.builder("money.movement.waiting", permits, Semaphore::getQueueLength)
                .description("Money movements waiting for a free slot")
                .register(meterRegistry);
    }

    @Around("execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.processTransfer(..))"
            + " || execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.processDeposit(..))"
            + " || execution(* com.suhasan.finance.transaction_service.service.TransactionServiceImpl.processWithdrawal(..))")
    public Object limit(ProceedingJoinPoint joinPoint) throws Throwable {
        int depth = DEPTH.get();
        if (depth > 0) {
            return enter(joinPoint, depth);
        }
        boolean acquired;
        try {
            acquired = permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MoneyMovementBusyException();
        }
        if (!acquired) {
            log.warn("Money movement rejected after waiting {} ms: every slot is busy, {} still waiting",
                    waitMillis, permits.getQueueLength());
            throw new MoneyMovementBusyException();
        }
        try {
            return enter(joinPoint, depth);
        } finally {
            permits.release();
        }
    }

    private Object enter(ProceedingJoinPoint joinPoint, int depth) throws Throwable {
        DEPTH.set(depth + 1);
        try {
            return joinPoint.proceed();
        } finally {
            if (depth == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(depth);
            }
        }
    }
}
