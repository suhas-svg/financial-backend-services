package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    Optional<Notification> findByDedupeKey(String dedupeKey);

    Optional<Notification> findByNotificationIdAndUserId(Long notificationId, String userId);

    long countByUserId(String userId);

    long countByUserIdAndStatus(String userId, NotificationStatus status);

    long countByUserIdAndSeverity(String userId, NotificationSeverity severity);

    long countByUserIdAndType(String userId, NotificationType type);

    List<Notification> findByUserIdAndStatus(String userId, NotificationStatus status);

    long countByUserIdAndSourceType(String userId, NotificationSourceType sourceType);
}
