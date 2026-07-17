package com.suhasan.finance.account_service.integration;

import com.suhasan.finance.account_service.entity.Notification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationProviderBoundaryTest {
    @Test
    void localAdapterReturnsReceiptWithoutClaimingExternalDelivery() {
        Notification notification = new Notification();
        notification.setNotificationId(42L);
        var receipt = new LocalInboxNotificationProvider().deliver(notification);

        assertThat(receipt.classification()).isEqualTo(NotificationProvider.Classification.ACCEPTED);
        assertThat(receipt.reconciliationStatus()).isEqualTo("MATCHED");
        assertThat(receipt.detail()).contains("Non-production");
    }

    @Test
    void missingAdapterClassifiesFailureAsUnavailable() {
        Notification notification = new Notification();
        var receipt = new FailClosedNotificationProvider().deliver(notification);

        assertThat(receipt.classification()).isEqualTo(NotificationProvider.Classification.UNAVAILABLE);
        assertThat(receipt.reconciliationStatus()).isEqualTo("UNRECONCILED");
    }
}
