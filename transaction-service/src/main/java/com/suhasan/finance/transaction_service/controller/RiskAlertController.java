package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.RiskAlertFilter;
import com.suhasan.finance.transaction_service.dto.RiskAlertResponse;
import com.suhasan.finance.transaction_service.dto.RiskAlertStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.RiskSummaryResponse;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import com.suhasan.finance.transaction_service.service.RiskAlertQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskAlertController {

    private final RiskAlertQueryService riskAlertQueryService;

    @GetMapping("/alerts")
    public ResponseEntity<Page<RiskAlertResponse>> listAlerts(
            @RequestParam(required = false) RiskAlertStatus status,
            @RequestParam(required = false) RiskAlertSeverity severity,
            @RequestParam(required = false) RiskAlertType alertType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        RiskAlertFilter filter = RiskAlertFilter.builder()
                .status(status)
                .severity(severity)
                .alertType(alertType)
                .userId(userId)
                .transactionId(transactionId)
                .from(from)
                .to(to)
                .build();
        return ResponseEntity.ok(riskAlertQueryService.searchAlerts(filter, pageable));
    }

    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<RiskAlertResponse> getAlert(@PathVariable String alertId) {
        return ResponseEntity.ok(riskAlertQueryService.getAlert(alertId));
    }

    @GetMapping("/summary")
    public ResponseEntity<RiskSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(riskAlertQueryService.getSummary(from, to));
    }

    @PatchMapping("/alerts/{alertId}/status")
    public ResponseEntity<RiskAlertResponse> updateStatus(
            @PathVariable String alertId,
            @RequestBody RiskAlertStatusUpdateRequest request,
            Authentication authentication) {
        String reviewer = authentication != null ? authentication.getName() : "SYSTEM";
        return ResponseEntity.ok(riskAlertQueryService.updateStatus(alertId, request, reviewer));
    }
}
