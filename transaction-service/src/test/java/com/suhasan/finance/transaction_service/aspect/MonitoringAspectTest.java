package com.suhasan.finance.transaction_service.aspect;

import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.service.AlertingService;
import com.suhasan.finance.transaction_service.service.MetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringAspectTest {
    private final MetricsService metrics = mock(MetricsService.class);
    private final AlertingService alerts = mock(AlertingService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MonitoringAspect aspect = new MonitoringAspect(metrics, alerts, registry);

    @Test
    void recordsSuccessfulAndFailedAccountCalls() throws Throwable {
        ProceedingJoinPoint success = joinPoint("getAccount", Object.class, new Object[0], "account");
        assertThat(aspect.monitorAccountServiceCalls(success)).isEqualTo("account");
        verify(alerts).resetAccountServiceErrorCount();

        ProceedingJoinPoint failure = joinPoint("getAccount", Object.class, new Object[0],
                new IllegalStateException("down"));
        assertThatThrownBy(() -> aspect.monitorAccountServiceCalls(failure))
                .isInstanceOf(IllegalStateException.class);
        verify(alerts).recordAccountServiceError();
        verify(metrics).recordAccountServiceError("getAccount");
        assertThat(registry.find("account.service.call.duration").timers()).hasSize(2);
    }

    @Test
    void recordsTransactionSuccessAndFailureWithResolvedTypes() throws Throwable {
        ProceedingJoinPoint transfer = joinPoint("processTransfer", Object.class, new Object[0], "done");
        assertThat(aspect.monitorTransactionProcessing(transfer)).isEqualTo("done");

        ProceedingJoinPoint depositFailure = joinPoint("process", Object.class,
                new Object[]{TransactionType.DEPOSIT}, new IllegalArgumentException("bad"));
        assertThatThrownBy(() -> aspect.monitorTransactionProcessing(depositFailure))
                .isInstanceOf(IllegalArgumentException.class);
        verify(metrics).recordTransactionFailed(TransactionType.DEPOSIT, "IllegalArgumentException");

        assertThat(aspect.monitorTransactionProcessing(
                joinPoint("process", Object.class, new Object[]{"withdraw"}, "ok"))).isEqualTo("ok");
        assertThat(aspect.monitorTransactionProcessing(
                joinPoint("processReverse", Object.class, new Object[0], "ok"))).isEqualTo("ok");
        assertThat(aspect.monitorTransactionProcessing(
                joinPoint("process", Object.class, new Object[]{"unclassified"}, "ok"))).isEqualTo("ok");
        assertThat(registry.find("transaction.processing.duration").timers()).isNotEmpty();
    }

    @Test
    void recordsSuccessfulAndFailedRepositoryOperations() throws Throwable {
        ProceedingJoinPoint success = joinPoint("save", ExampleRepository.class, new Object[0], "saved");
        assertThat(aspect.monitorDatabaseOperations(success)).isEqualTo("saved");

        ProceedingJoinPoint failure = joinPoint("save", ExampleRepository.class, new Object[0],
                new IllegalStateException("database down"));
        assertThatThrownBy(() -> aspect.monitorDatabaseOperations(failure))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.find("database.operation.duration").timers()).hasSize(2);
        assertThat(registry.get("database.operation.error.total").counter().count()).isEqualTo(1);
    }

    private ProceedingJoinPoint joinPoint(String method, Class<?> owner, Object[] args, Object result)
            throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(owner);
        when(joinPoint.getArgs()).thenReturn(args);
        if (result instanceof Throwable throwable) {
            when(joinPoint.proceed()).thenThrow(throwable);
        } else {
            when(joinPoint.proceed()).thenReturn(result);
        }
        return joinPoint;
    }

    private interface ExampleRepository {}
}
