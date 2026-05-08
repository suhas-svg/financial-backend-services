package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.AuditEventFilter;
import com.suhasan.finance.transaction_service.dto.AuditLogEntryResponse;
import com.suhasan.finance.transaction_service.dto.AuditSummaryResponse;
import com.suhasan.finance.transaction_service.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping("/events")
    public ResponseEntity<Page<AuditLogEntryResponse>> listEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        AuditEventFilter filter = AuditEventFilter.builder()
                .eventType(eventType)
                .action(action)
                .outcome(outcome)
                .userId(userId)
                .transactionId(transactionId)
                .from(from)
                .to(to)
                .build();
        return ResponseEntity.ok(auditQueryService.searchEvents(filter, pageable));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<AuditLogEntryResponse> getEvent(@PathVariable String eventId) {
        return ResponseEntity.ok(auditQueryService.getEvent(eventId));
    }

    @GetMapping("/summary")
    public ResponseEntity<AuditSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(auditQueryService.getSummary(from, to));
    }
}
