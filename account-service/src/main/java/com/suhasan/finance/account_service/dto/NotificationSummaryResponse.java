package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notification counts for the signed-in user. Every enum value is present in each map (zero
 * when there are none), so clients can render all categories without guessing. The maps are
 * unmodifiable copies that keep the caller's order (enum declaration order).
 */
public record NotificationSummaryResponse(
        long total,
        long unread,
        Map<NotificationSeverity, Long> bySeverity,
        Map<NotificationType, Long> byType,
        Map<NotificationSourceType, Long> bySourceType) {

    public NotificationSummaryResponse {
        bySeverity = Collections.unmodifiableMap(new LinkedHashMap<>(bySeverity));
        byType = Collections.unmodifiableMap(new LinkedHashMap<>(byType));
        bySourceType = Collections.unmodifiableMap(new LinkedHashMap<>(bySourceType));
    }
}
