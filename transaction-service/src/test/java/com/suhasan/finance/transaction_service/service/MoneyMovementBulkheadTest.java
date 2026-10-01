package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.exception.MoneyMovementBusyException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoneyMovementBulkheadTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static ProceedingJoinPoint returning(Object value) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(value);
        return joinPoint;
    }

    @Test
    void runsTheMovementWhenASlotIsFree() throws Throwable {
        MoneyMovementBulkhead bulkhead = new MoneyMovementBulkhead(2, 100, registry);

        assertThat(bulkhead.limit(returning("done"))).isEqualTo("done");
        assertThat(registry.get("money.movement.in_flight").gauge().value()).isZero();
    }

    @Test
    void rejectsFastWithoutRunningWhenEverySlotStaysBusy() throws Throwable {
        MoneyMovementBulkhead bulkhead = new MoneyMovementBulkhead(1, 50, registry);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProceedingJoinPoint slow = mock(ProceedingJoinPoint.class);
        when(slow.proceed()).thenAnswer(invocation -> {
            inside.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "slow";
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> holder = pool.submit(() -> {
                try {
                    return bulkhead.limit(slow);
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.get("money.movement.in_flight").gauge().value()).isEqualTo(1.0);

            ProceedingJoinPoint rejected = mock(ProceedingJoinPoint.class);
            assertThatThrownBy(() -> bulkhead.limit(rejected)).isInstanceOf(MoneyMovementBusyException.class);
            org.mockito.Mockito.verifyNoInteractions(rejected);

            release.countDown();
            assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("slow");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aMovementInsideAMovementDoesNotWaitForItsOwnSlot() throws Throwable {
        MoneyMovementBulkhead bulkhead = new MoneyMovementBulkhead(1, 50, registry);
        ProceedingJoinPoint inner = returning("inner");
        ProceedingJoinPoint outer = mock(ProceedingJoinPoint.class);
        when(outer.proceed()).thenAnswer(invocation -> bulkhead.limit(inner));

        assertThat(bulkhead.limit(outer)).isEqualTo("inner");
        assertThat(registry.get("money.movement.in_flight").gauge().value()).isZero();
    }

    @Test
    void releasesTheSlotWhenTheMovementFails() throws Throwable {
        MoneyMovementBulkhead bulkhead = new MoneyMovementBulkhead(1, 50, registry);
        ProceedingJoinPoint failing = mock(ProceedingJoinPoint.class);
        when(failing.proceed()).thenThrow(new IllegalStateException("declined"));

        assertThatThrownBy(() -> bulkhead.limit(failing)).hasMessage("declined");
        assertThat(bulkhead.limit(returning("next"))).isEqualTo("next");
    }

    @Test
    void refusesANonPositiveLimit() {
        assertThatThrownBy(() -> new MoneyMovementBulkhead(0, 50, registry))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
