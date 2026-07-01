package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    @DisplayName("Creates unread notification with trimmed title and message")
    void createsUnreadNotificationWithTrimmedTitleAndMessage() {
        when(notificationRepository.findByDedupeKey("transaction:tx-1:COMPLETED")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notification created = notificationService.createInternal(NotificationCreateRequest.builder()
                .userId("customer")
                .type(NotificationType.TRANSACTION_COMPLETED)
                .severity(NotificationSeverity.SUCCESS)
                .title("  Transfer completed  ")
                .message("  Your transfer was completed.  ")
                .sourceType(NotificationSourceType.TRANSACTION)
                .sourceId("tx-1")
                .dedupeKey("transaction:tx-1:COMPLETED")
                .build());

        assertThat(created.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(created.getTitle()).isEqualTo("Transfer completed");
        assertThat(created.getMessage()).isEqualTo("Your transfer was completed.");
        assertThat(created.getCreatedAt()).isNotNull();
        verify(notificationRepository).save(created);
    }

    @Test
    @DisplayName("Returns existing notification for duplicate dedupe key")
    void returnsExistingNotificationForDuplicateDedupeKey() {
        Notification existing = Notification.builder()
                .notificationId(10L)
                .userId("customer")
                .dedupeKey("dispute:dp-1:created")
                .createdAt(LocalDateTime.now())
                .build();
        when(notificationRepository.findByDedupeKey("dispute:dp-1:created")).thenReturn(Optional.of(existing));

        Notification result = notificationService.createInternal(NotificationCreateRequest.builder()
                .userId("customer")
                .type(NotificationType.DISPUTE_CREATED)
                .severity(NotificationSeverity.INFO)
                .title("Dispute opened")
                .message("We opened your dispute.")
                .sourceType(NotificationSourceType.DISPUTE)
                .sourceId("dp-1")
                .dedupeKey("dispute:dp-1:created")
                .build());

        assertThat(result).isSameAs(existing);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void createNotification_acceptsScheduledTransferLifecycleType() {
        when(notificationRepository.findByDedupeKey("scheduled-transfer:schedule-1:created")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationCreateRequest request = NotificationCreateRequest.builder()
                .userId("customer")
                .type(NotificationType.SCHEDULED_TRANSFER_CREATED)
                .severity(NotificationSeverity.INFO)
                .title("Scheduled transfer created")
                .message("Your scheduled transfer was created.")
                .sourceType(NotificationSourceType.SCHEDULED_TRANSFER)
                .sourceId("schedule-1")
                .dedupeKey("scheduled-transfer:schedule-1:created")
                .build();

        Notification response = notificationService.createInternal(request);

        assertThat(response.getType()).isEqualTo(NotificationType.SCHEDULED_TRANSFER_CREATED);
        assertThat(response.getSourceType()).isEqualTo(NotificationSourceType.SCHEDULED_TRANSFER);
    }

    @Test
    @DisplayName("Rejects missing required create fields")
    void rejectsMissingRequiredCreateFields() {
        assertThatThrownBy(() -> notificationService.createInternal(NotificationCreateRequest.builder()
                .userId("customer")
                .severity(NotificationSeverity.INFO)
                .title(" ")
                .message("Message")
                .sourceType(NotificationSourceType.ACCOUNT)
                .sourceId("1")
                .dedupeKey("account:1")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Notification user, type, severity, title, message, source, and dedupe key are required");
    }

    @Test
    @DisplayName("Marks only owned unread notification as read")
    void marksOnlyOwnedUnreadNotificationAsRead() {
        Notification notification = Notification.builder()
                .notificationId(5L)
                .userId("customer")
                .status(NotificationStatus.UNREAD)
                .build();
        when(notificationRepository.findByNotificationIdAndUserId(5L, "customer")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = notificationService.markRead(5L, "customer");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(result.getReadAt()).isNotNull();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getNotificationId()).isEqualTo(5L);
    }
}
