package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query(value = """
            SELECT n.* FROM notifications n
             WHERE NOT EXISTS (
                 SELECT 1 FROM notification_provider_receipts r
                  WHERE r.notification_id=n.notification_id AND r.provider=:provider)
             ORDER BY n.created_at
             FOR UPDATE SKIP LOCKED
             LIMIT :limit
            """, nativeQuery = true)
    List<Notification> claimUnreceipted(@Param("provider") String provider, @Param("limit") int limit);

    @Query(value = """
            SELECT COUNT(*) FROM notifications n
             WHERE NOT EXISTS (
                 SELECT 1 FROM notification_provider_receipts r
                  WHERE r.notification_id=n.notification_id AND r.provider=:provider)
            """, nativeQuery = true)
    long countUnreceipted(@Param("provider") String provider);

    @Query(value = """
            SELECT MIN(n.created_at) FROM notifications n
             WHERE NOT EXISTS (
                 SELECT 1 FROM notification_provider_receipts r
                  WHERE r.notification_id=n.notification_id AND r.provider=:provider)
            """, nativeQuery = true)
    java.time.LocalDateTime oldestUnreceipted(@Param("provider") String provider);
}
