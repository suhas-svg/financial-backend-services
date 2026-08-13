package com.suhasan.finance.transaction_service.aspect;

import com.suhasan.finance.transaction_service.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ApiAccessAuditRecorderTest {

    @Test
    void dispatchesApiAccessPersistenceOffTheRequestThread() {
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        TaskExecutor executor = submitted::set;
        AuditService auditService = mock(AuditService.class);
        ApiAccessAuditRecorder recorder = new ApiAccessAuditRecorder(executor, auditService);

        recorder.record("/api/transactions", "GET", "customer", "127.0.0.1", 200, 17);

        verifyNoInteractions(auditService);
        submitted.get().run();
        verify(auditService).logApiAccess("/api/transactions", "GET", "customer", "127.0.0.1", 200, 17);
    }

    @Test
    void fallsBackToSynchronousPersistenceWhenExecutorRejects() {
        TaskExecutor executor = mock(TaskExecutor.class);
        doThrow(new IllegalStateException("executor unavailable")).when(executor).execute(org.mockito.ArgumentMatchers.any());
        AuditService auditService = mock(AuditService.class);
        ApiAccessAuditRecorder recorder = new ApiAccessAuditRecorder(executor, auditService);

        recorder.record("/api/transactions", "POST", "customer", "127.0.0.1", 409, 23);

        verify(auditService).logApiAccess("/api/transactions", "POST", "customer", "127.0.0.1", 409, 23);
    }
}
