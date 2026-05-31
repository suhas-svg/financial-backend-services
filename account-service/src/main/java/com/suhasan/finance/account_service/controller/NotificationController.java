package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.dto.NotificationFilter;
import com.suhasan.finance.account_service.entity.Notification;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationStatus;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.service.NotificationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Dependencies are injected and managed by Spring")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public ResponseEntity<Page<Notification>> list(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationSeverity severity,
            @RequestParam(required = false) NotificationSourceType sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication authentication
    ) {
        NotificationFilter filter = new NotificationFilter(status, type, severity, sourceType, from, to);
        return ResponseEntity.ok(notificationService.listForUser(authentication.getName(), filter, pageable));
    }

    @GetMapping("/notifications/summary")
    public ResponseEntity<Map<String, Object>> summary(Authentication authentication) {
        return ResponseEntity.ok(notificationService.summaryForUser(authentication.getName()));
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Notification> markRead(@PathVariable Long notificationId, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markRead(notificationId, authentication.getName()));
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(Authentication authentication) {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(authentication.getName())));
    }

    @PostMapping("/internal/notifications")
    public ResponseEntity<Notification> createInternal(
            @RequestBody NotificationCreateRequest request,
            Authentication authentication
    ) {
        if (!isAdmin(authentication) && !isInternalService(authentication)) {
            throw new AccessDeniedException("Only admins or internal services can create notifications");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createInternal(request));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean isInternalService(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INTERNAL_SERVICE".equals(a.getAuthority()));
    }
}
