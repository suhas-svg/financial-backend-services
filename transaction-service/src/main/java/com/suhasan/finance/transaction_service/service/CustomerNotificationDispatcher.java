package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class CustomerNotificationDispatcher {

    private final ResilientAccountServiceClient accountServiceClient;

    private final TaskExecutor executor;

    public CustomerNotificationDispatcher(
            ResilientAccountServiceClient accountServiceClient,
            @Qualifier("customerNotificationExecutor") TaskExecutor executor) {
        this.accountServiceClient = accountServiceClient;
        this.executor = executor;
    }

    public void dispatchAfterCommit(ResilientAccountServiceClient.NotificationRequest request) {
        Runnable dispatch = () -> submit(request);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
            return;
        }
        dispatch.run();
    }

    private void submit(ResilientAccountServiceClient.NotificationRequest request) {
        try {
            executor.execute(() -> deliver(request));
        } catch (RuntimeException e) {
            log.warn("Customer notification dispatch was rejected for source {}: {}",
                    request.getSourceId(), e.getMessage());
        }
    }

    private void deliver(ResilientAccountServiceClient.NotificationRequest request) {
        try {
            accountServiceClient.createNotification(request);
        } catch (RuntimeException e) {
            log.warn("Customer notification delivery failed for source {}: {}",
                    request.getSourceId(), e.getMessage());
        }
    }
}
