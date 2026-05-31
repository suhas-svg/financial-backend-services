package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCreateRequest {
    private String userId;
    private NotificationType type;
    private NotificationSeverity severity;
    private String title;
    private String message;
    private NotificationSourceType sourceType;
    private String sourceId;
    private String dedupeKey;
}
