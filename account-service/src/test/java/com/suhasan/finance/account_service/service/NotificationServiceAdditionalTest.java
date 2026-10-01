package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.dto.NotificationFilter;
import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.repository.NotificationRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceAdditionalTest {
    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository);
    }

    @Test
    void rejectsConflictingDedupeOwnershipSourceAndDeliveryId() {
        Notification existing = baseNotification();
        when(repository.findByDedupeKey("key")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.createInternal(request("other", "key", "delivery")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ownership");

        existing.setUserId("user");
        existing.setDeliveryId("original");
        assertThatThrownBy(() -> service.createInternal(request("user", "key", "different")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("delivery");
    }

    @Test
    void concurrentInsertWithoutWinnerRethrowsOriginalIntegrityFailure() {
        when(repository.findByDedupeKey("key")).thenReturn(Optional.empty());
        var failure = new DataIntegrityViolationException("race");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.createInternal(request("user", "key", null))).isSameAs(failure);
    }

    @Test
    void fillsMissingDeliveryIdAndNormalizesZeroDeliveryCount() {
        Notification existing = baseNotification();
        existing.setDeliveryId(null);
        existing.setDeliveryCount(0);
        when(repository.findByDedupeKey("key")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        Notification result = service.createInternal(request("user", "key", " delivery "));
        assertThat(result.getDeliveryId()).isEqualTo("delivery");
        assertThat(result.getDeliveryCount()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsSummaryAcrossEveryEnumDimension() {
        when(repository.countByUserGrouped("user")).thenReturn(List.of(
                countRow(NotificationStatus.UNREAD, NotificationSeverity.CRITICAL, NotificationType.ACCOUNT_FROZEN,
                        NotificationSourceType.ACCOUNT, 3L),
                countRow(NotificationStatus.READ, NotificationSeverity.CRITICAL, NotificationType.TRANSACTION_COMPLETED,
                        NotificationSourceType.TRANSACTION, 5L),
                countRow(NotificationStatus.READ, NotificationSeverity.INFO, NotificationType.TRANSACTION_COMPLETED,
                        NotificationSourceType.TRANSACTION, 4L)));
        var summary = service.summaryForUser("user");
        assertThat(summary).containsEntry("total", 12L).containsEntry("unread", 3L);
        var bySeverity = (java.util.Map<Object, Object>) summary.get("bySeverity");
        var byType = (java.util.Map<Object, Object>) summary.get("byType");
        var bySourceType = (java.util.Map<Object, Object>) summary.get("bySourceType");
        assertThat(bySeverity).hasSize(NotificationSeverity.values().length)
                .containsEntry(NotificationSeverity.CRITICAL, 8L)
                .containsEntry(NotificationSeverity.INFO, 4L)
                .containsEntry(NotificationSeverity.WARNING, 0L);
        assertThat(byType).hasSize(NotificationType.values().length)
                .containsEntry(NotificationType.TRANSACTION_COMPLETED, 9L)
                .containsEntry(NotificationType.ACCOUNT_FROZEN, 3L)
                .containsEntry(NotificationType.DISPUTE_CREATED, 0L);
        assertThat(bySourceType).hasSize(NotificationSourceType.values().length)
                .containsEntry(NotificationSourceType.TRANSACTION, 9L)
                .containsEntry(NotificationSourceType.ACCOUNT, 3L);
    }

    @Test
    void handlesAlreadyReadMissingAndBulkReadPaths() {
        Notification read = baseNotification();
        read.setStatus(NotificationStatus.READ);
        when(repository.findByNotificationIdAndUserId(1L, "user")).thenReturn(Optional.of(read));
        assertThat(service.markRead(1L, "user")).isSameAs(read);
        when(repository.findByNotificationIdAndUserId(2L, "user")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markRead(2L, "user")).isInstanceOf(IllegalArgumentException.class);

        when(repository.markAllUnreadAsRead(org.mockito.ArgumentMatchers.eq("user"), any(LocalDateTime.class)))
                .thenReturn(2);
        assertThat(service.markAllRead("user")).isEqualTo(2);
        verify(repository).markAllUnreadAsRead(org.mockito.ArgumentMatchers.eq("user"), any(LocalDateTime.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void buildsUserSpecificationWithAllOptionalFiltersAndWithoutFilter() {
        var page = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(page)))
                .thenReturn(new PageImpl<>(List.of()));
        service.listForUser("user", null, page);

        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        NotificationFilter filter = new NotificationFilter(
                NotificationStatus.UNREAD, NotificationType.ACCOUNT_FROZEN,
                NotificationSeverity.CRITICAL, NotificationSourceType.ACCOUNT, from, to);
        service.listForUser("user", filter, page);

        var captor = org.mockito.ArgumentCaptor.forClass(Specification.class);
        verify(repository, org.mockito.Mockito.times(2)).findAll(captor.capture(), org.mockito.ArgumentMatchers.eq(page));
        Root<Notification> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get(any(String.class))).thenReturn(path);
        when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        assertThat(captor.getAllValues().get(0).toPredicate(root, query, cb)).isSameAs(predicate);
        assertThat(captor.getAllValues().get(1).toPredicate(root, query, cb)).isSameAs(predicate);
    }

    @Test
    void validatesEveryRequiredCreateBoundary() {
        assertThatThrownBy(() -> service.createInternal(null)).isInstanceOf(IllegalArgumentException.class);
        for (NotificationCreateRequest invalid : List.of(
                request(" ", "key", null),
                NotificationCreateRequest.builder().userId("user").severity(NotificationSeverity.INFO)
                        .title("title").message("message").sourceType(NotificationSourceType.ACCOUNT)
                        .sourceId("1").dedupeKey("key").build(),
                NotificationCreateRequest.builder().userId("user").type(NotificationType.ACCOUNT_FROZEN)
                        .title("title").message("message").sourceType(NotificationSourceType.ACCOUNT)
                        .sourceId("1").dedupeKey("key").build(),
                NotificationCreateRequest.builder().userId("user").type(NotificationType.ACCOUNT_FROZEN)
                        .severity(NotificationSeverity.INFO).title(" ").message("message")
                        .sourceType(NotificationSourceType.ACCOUNT).sourceId("1").dedupeKey("key").build())) {
            assertThatThrownBy(() -> service.createInternal(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Notification baseNotification() {
        return Notification.builder().notificationId(1L).userId("user")
                .type(NotificationType.ACCOUNT_FROZEN).severity(NotificationSeverity.CRITICAL)
                .status(NotificationStatus.UNREAD).sourceType(NotificationSourceType.ACCOUNT)
                .sourceId("1").dedupeKey("key").deliveryCount(1).build();
    }

    private NotificationCreateRequest request(String user, String key, String delivery) {
        return NotificationCreateRequest.builder().userId(user).type(NotificationType.ACCOUNT_FROZEN)
                .severity(NotificationSeverity.CRITICAL).title("title").message("message")
                .sourceType(NotificationSourceType.ACCOUNT).sourceId("1")
                .dedupeKey(key).deliveryId(delivery).build();
    }

    private static NotificationRepository.NotificationCountRow countRow(
            NotificationStatus status, NotificationSeverity severity, NotificationType type,
            NotificationSourceType sourceType, long total) {
        return new NotificationRepository.NotificationCountRow() {
            @Override public NotificationStatus getStatus() { return status; }
            @Override public NotificationSeverity getSeverity() { return severity; }
            @Override public NotificationType getType() { return type; }
            @Override public NotificationSourceType getSourceType() { return sourceType; }
            @Override public long getTotal() { return total; }
        };
    }
}
