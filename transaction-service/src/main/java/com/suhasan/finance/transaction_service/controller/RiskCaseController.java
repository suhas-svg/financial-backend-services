package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.RiskCaseCreateRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseFilter;
import com.suhasan.finance.transaction_service.dto.RiskCaseNoteRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseResponse;
import com.suhasan.finance.transaction_service.dto.RiskCaseStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseSummaryResponse;
import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import com.suhasan.finance.transaction_service.service.RiskCaseService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskCaseController {

    private final RiskCaseService riskCaseService;

    @GetMapping("/cases")
    public ResponseEntity<Page<RiskCaseResponse>> listCases(
            @RequestParam(required = false) RiskCaseStatus status,
            @RequestParam(required = false) RiskCasePriority priority,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) String alertId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        RiskCaseFilter filter = RiskCaseFilter.builder()
                .status(status)
                .priority(priority)
                .assignedTo(assignedTo)
                .userId(userId)
                .transactionId(transactionId)
                .alertId(alertId)
                .from(from)
                .to(to)
                .build();
        return ResponseEntity.ok(riskCaseService.searchCases(filter, pageable));
    }

    @GetMapping("/cases/summary")
    public ResponseEntity<RiskCaseSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(riskCaseService.getSummary(from, to));
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<RiskCaseResponse> getCase(@PathVariable String caseId) {
        return ResponseEntity.ok(riskCaseService.getCase(caseId));
    }

    @PostMapping("/cases/from-alert/{alertId}")
    public ResponseEntity<RiskCaseResponse> createFromAlert(
            @PathVariable String alertId,
            @RequestBody(required = false) RiskCaseCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(riskCaseService.createFromAlert(alertId, request, currentUser(authentication)));
    }

    @PatchMapping("/cases/{caseId}/claim")
    public ResponseEntity<RiskCaseResponse> claimCase(@PathVariable String caseId, Authentication authentication) {
        return ResponseEntity.ok(riskCaseService.claimCase(caseId, currentUser(authentication)));
    }

    @PatchMapping("/cases/{caseId}/status")
    public ResponseEntity<RiskCaseResponse> updateStatus(
            @PathVariable String caseId,
            @RequestBody RiskCaseStatusUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(riskCaseService.updateStatus(caseId, request, currentUser(authentication)));
    }

    @PostMapping("/cases/{caseId}/notes")
    public ResponseEntity<RiskCaseResponse> addNote(
            @PathVariable String caseId,
            @RequestBody RiskCaseNoteRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(riskCaseService.addNote(caseId, request, currentUser(authentication)));
    }

    private String currentUser(Authentication authentication) {
        return authentication != null ? authentication.getName() : "SYSTEM";
    }
}
