package com.suhasan.finance.account_service.repository;

import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    Optional<Notification> findByDedupeKey(String dedupeKey);

    Optional<Notification> findByNotificationIdAndUserId(Long notificationId, String userId);

    /** One row per (status, severity, type, sourceType) combination the user has notifications in. */
    interface NotificationCountRow {
        NotificationStatus getStatus();

        NotificationSeverity getSeverity();

        NotificationType getType();

        NotificationSourceType getSourceType();

        long getTotal();
    }

    @Query("""
            SELECT n.status AS status, n.severity AS severity, n.type AS type,
                   n.sourceType AS sourceType, COUNT(n) AS total
              FROM Notification n
             WHERE n.userId = :userId
             GROUP BY n.status, n.severity, n.type, n.sourceType
            """)
    List<NotificationCountRow> countByUserGrouped(@Param("userId") String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Notification n
               SET n.status = com.suhasan.finance.account_service.entity.NotificationStatus.READ,
                   n.readAt = :readAt
             WHERE n.userId = :userId
               AND n.status = com.suhasan.finance.account_service.entity.NotificationStatus.UNREAD
            """)
    int markAllUnreadAsRead(@Param("userId") String userId, @Param("readAt") LocalDateTime readAt);

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
    LocalDateTime oldestUnreceipted(@Param("provider") String provider);
}
