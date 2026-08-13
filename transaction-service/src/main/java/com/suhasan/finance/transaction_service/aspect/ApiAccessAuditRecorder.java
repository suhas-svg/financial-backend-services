package com.suhasan.finance.transaction_service.aspect;

import com.suhasan.finance.transaction_service.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ApiAccessAuditRecorder {
    private final TaskExecutor executor;
    private final AuditService auditService;

    public ApiAccessAuditRecorder(
            @Qualifier("apiAccessAuditExecutor") TaskExecutor executor,
            AuditService auditService) {
        this.executor = executor;
        this.auditService = auditService;
    }

    public void record(String endpoint, String method, String userId, String ipAddress,
                       int responseStatus, long responseTime) {
        Runnable write = () -> auditService.logApiAccess(
                endpoint, method, userId, ipAddress, responseStatus, responseTime);
        try {
            executor.execute(write);
        } catch (RuntimeException rejected) {
            log.warn("API access audit executor rejected work; persisting on the request thread", rejected);
            write.run();
        }
    }
}
