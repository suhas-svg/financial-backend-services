package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    private NotificationService notificationService;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        controller = new NotificationController(notificationService);
    }

    @Test
    @DisplayName("Customer lists only current user's notifications")
    void customerListsOnlyCurrentUsersNotifications() {
        Notification notification = Notification.builder()
                .notificationId(1L)
                .userId("customer")
                .title("Transfer completed")
                .build();
        when(notificationService.listForUser(any(), any(), any())).thenReturn(new PageImpl<>(List.of(notification)));

        var response = controller.list(null, null, null, null, null, null, PageRequest.of(0, 20), auth("customer", "ROLE_USER"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).containsExactly(notification);
        verify(notificationService).listForUser(any(), any(), any());
    }

    @Test
    @DisplayName("Customer marks own notification read")
    void customerMarksOwnNotificationRead() {
        Notification notification = Notification.builder()
                .notificationId(7L)
                .userId("customer")
                .status(NotificationStatus.READ)
                .build();
        when(notificationService.markRead(7L, "customer")).thenReturn(notification);

        var response = controller.markRead(7L, auth("customer", "ROLE_USER"));

        assertThat(response.getBody()).isSameAs(notification);
        verify(notificationService).markRead(7L, "customer");
    }

    @Test
    @DisplayName("Admin can create internal notification for any user")
    void adminCanCreateInternalNotificationForAnyUser() {
        NotificationCreateRequest request = NotificationCreateRequest.builder()
                .userId("customer")
                .type(NotificationType.ACCOUNT_FROZEN)
                .severity(NotificationSeverity.CRITICAL)
                .title("Account frozen")
                .message("Your account was frozen.")
                .sourceType(NotificationSourceType.ACCOUNT)
                .sourceId("1")
                .dedupeKey("account-status:1:FROZEN:now")
                .build();
        Notification notification = Notification.builder().notificationId(11L).userId("customer").build();
        when(notificationService.createInternal(request)).thenReturn(notification);

        var response = controller.createInternal(request, auth("admin", "ROLE_ADMIN"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isSameAs(notification);
    }

    @Test
    @DisplayName("Normal user cannot create internal notifications")
    void normalUserCannotCreateInternalNotifications() {
        assertThatThrownBy(() -> controller.createInternal(NotificationCreateRequest.builder().build(), auth("customer", "ROLE_USER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only admins or internal services can create notifications");
    }

    @Test
    @DisplayName("Summary delegates to current user")
    void summaryDelegatesToCurrentUser() {
        when(notificationService.summaryForUser("customer")).thenReturn(Map.of("total", 2L, "unread", 1L));

        var response = controller.summary(auth("customer", "ROLE_USER"));

        assertThat(response.getBody()).containsEntry("total", 2L);
        verify(notificationService).summaryForUser("customer");
    }

    private TestingAuthenticationToken auth(String name, String role) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(name, "token", role);
        authentication.setAuthenticated(true);
        return authentication;
    }
}
