package com.suhasan.finance.account_service.dto;

import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationFilter {
    private NotificationStatus status;
    private NotificationType type;
    private NotificationSeverity severity;
    private NotificationSourceType sourceType;
    private LocalDateTime from;
    private LocalDateTime to;
}
