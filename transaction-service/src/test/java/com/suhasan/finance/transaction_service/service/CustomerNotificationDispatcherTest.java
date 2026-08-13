package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CustomerNotificationDispatcherTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void dispatchesImmediatelyWhenNoTransactionIsActive() {
        ResilientAccountServiceClient client = mock(ResilientAccountServiceClient.class);
        CustomerNotificationDispatcher dispatcher = new CustomerNotificationDispatcher(client, new SyncTaskExecutor());
        ResilientAccountServiceClient.NotificationRequest request = request();

        dispatcher.dispatchAfterCommit(request);

        verify(client).createNotification(request);
    }

    @Test
    void waitsForCommitBeforeDispatching() {
        ResilientAccountServiceClient client = mock(ResilientAccountServiceClient.class);
        CustomerNotificationDispatcher dispatcher = new CustomerNotificationDispatcher(client, new SyncTaskExecutor());
        ResilientAccountServiceClient.NotificationRequest request = request();
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatchAfterCommit(request);
        verify(client, never()).createNotification(request);

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(client).createNotification(request);
    }

    @Test
    void downstreamFailureDoesNotEscapeToFinancialOperation() {
        ResilientAccountServiceClient client = mock(ResilientAccountServiceClient.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("provider unavailable"))
                .when(client).createNotification(org.mockito.ArgumentMatchers.any());
        CustomerNotificationDispatcher dispatcher = new CustomerNotificationDispatcher(client, new SyncTaskExecutor());

        dispatcher.dispatchAfterCommit(request());
    }

    private ResilientAccountServiceClient.NotificationRequest request() {
        return ResilientAccountServiceClient.NotificationRequest.builder()
                .userId("customer")
                .type("SCHEDULED_TRANSFER_CREATED")
                .severity("INFO")
                .title("Scheduled transfer created")
                .message("Created")
                .sourceType("SCHEDULED_TRANSFER")
                .sourceId("schedule-1")
                .dedupeKey("schedule-1:created")
                .build();
    }
}
