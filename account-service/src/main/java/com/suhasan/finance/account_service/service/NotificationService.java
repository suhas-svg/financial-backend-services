package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.dto.NotificationFilter;
import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.repository.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification createInternal(NotificationCreateRequest request) {
        validateCreate(request);
        return notificationRepository.findByDedupeKey(request.getDedupeKey().trim())
                .orElseGet(() -> notificationRepository.save(Notification.builder()
                        .userId(request.getUserId().trim())
                        .type(request.getType())
                        .severity(request.getSeverity())
                        .status(NotificationStatus.UNREAD)
                        .title(request.getTitle().trim())
                        .message(request.getMessage().trim())
                        .sourceType(request.getSourceType())
                        .sourceId(request.getSourceId().trim())
                        .dedupeKey(request.getDedupeKey().trim())
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForUser(String userId, NotificationFilter filter, Pageable pageable) {
        return notificationRepository.findAll(forUser(userId, filter), pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summaryForUser(String userId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", notificationRepository.countByUserId(userId));
        summary.put("unread", notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD));

        Map<NotificationSeverity, Long> bySeverity = new EnumMap<>(NotificationSeverity.class);
        for (NotificationSeverity severity : NotificationSeverity.values()) {
            bySeverity.put(severity, notificationRepository.countByUserIdAndSeverity(userId, severity));
        }
        summary.put("bySeverity", bySeverity);

        Map<NotificationType, Long> byType = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            byType.put(type, notificationRepository.countByUserIdAndType(userId, type));
        }
        summary.put("byType", byType);

        Map<NotificationSourceType, Long> bySourceType = new EnumMap<>(NotificationSourceType.class);
        for (NotificationSourceType sourceType : NotificationSourceType.values()) {
            bySourceType.put(sourceType, notificationRepository.countByUserIdAndSourceType(userId, sourceType));
        }
        summary.put("bySourceType", bySourceType);
        return summary;
    }

    @Transactional
    public Notification markRead(Long notificationId, String userId) {
        Notification notification = notificationRepository.findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (notification.getStatus() != NotificationStatus.READ) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
            return notificationRepository.save(notification);
        }
        return notification;
    }

    @Transactional
    public int markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndStatus(userId, NotificationStatus.UNREAD);
        LocalDateTime readAt = LocalDateTime.now();
        unread.forEach(notification -> {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(readAt);
        });
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    private Specification<Notification> forUser(String userId, NotificationFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (filter != null) {
                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                if (filter.getType() != null) {
                    predicates.add(cb.equal(root.get("type"), filter.getType()));
                }
                if (filter.getSeverity() != null) {
                    predicates.add(cb.equal(root.get("severity"), filter.getSeverity()));
                }
                if (filter.getSourceType() != null) {
                    predicates.add(cb.equal(root.get("sourceType"), filter.getSourceType()));
                }
                if (filter.getFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom()));
                }
                if (filter.getTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getTo()));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validateCreate(NotificationCreateRequest request) {
        if (request == null
                || !hasText(request.getUserId())
                || request.getType() == null
                || request.getSeverity() == null
                || !hasText(request.getTitle())
                || !hasText(request.getMessage())
                || request.getSourceType() == null
                || !hasText(request.getSourceId())
                || !hasText(request.getDedupeKey())) {
            throw new IllegalArgumentException("Notification user, type, severity, title, message, source, and dedupe key are required");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
