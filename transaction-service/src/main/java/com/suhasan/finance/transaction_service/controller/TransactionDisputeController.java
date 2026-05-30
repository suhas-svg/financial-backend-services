package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.DisputeCreateRequest;
import com.suhasan.finance.transaction_service.dto.DisputeFilter;
import com.suhasan.finance.transaction_service.dto.DisputeNoteRequest;
import com.suhasan.finance.transaction_service.dto.DisputeSummaryResponse;
import com.suhasan.finance.transaction_service.dto.DisputeStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.TransactionDisputeResponse;
import com.suhasan.finance.transaction_service.entity.DisputeReasonCode;
import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import com.suhasan.finance.transaction_service.service.TransactionDisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class TransactionDisputeController {

    private final TransactionDisputeService disputeService;

    @PostMapping
    public ResponseEntity<TransactionDisputeResponse> createDispute(
            @Valid @RequestBody DisputeCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disputeService.createDispute(request, currentUser(authentication)));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionDisputeResponse>> listCustomerDisputes(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(disputeService.listCustomerDisputes(currentUser(authentication), pageable));
    }

    @GetMapping("/{disputeId}")
    public ResponseEntity<TransactionDisputeResponse> getCustomerDispute(
            @PathVariable String disputeId,
            Authentication authentication) {
        return ResponseEntity.ok(disputeService.getCustomerDispute(disputeId, currentUser(authentication)));
    }

    @GetMapping("/admin")
    public ResponseEntity<Page<TransactionDisputeResponse>> searchAdminDisputes(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) DisputeReasonCode reasonCode,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        DisputeFilter filter = DisputeFilter.builder()
                .status(status)
                .reasonCode(reasonCode)
                .assignedTo(assignedTo)
                .userId(userId)
                .transactionId(transactionId)
                .from(from)
                .to(to)
                .build();
        return ResponseEntity.ok(disputeService.searchAdminDisputes(filter, pageable));
    }

    @GetMapping("/admin/summary")
    public ResponseEntity<DisputeSummaryResponse> getAdminSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(disputeService.getSummary(from, to));
    }

    @PatchMapping("/admin/{disputeId}/claim")
    public ResponseEntity<TransactionDisputeResponse> claimDispute(@PathVariable String disputeId, Authentication authentication) {
        return ResponseEntity.ok(disputeService.claimDispute(disputeId, currentUser(authentication)));
    }

    @PatchMapping("/admin/{disputeId}/status")
    public ResponseEntity<TransactionDisputeResponse> updateDisputeStatus(
            @PathVariable String disputeId,
            @Valid @RequestBody DisputeStatusUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(disputeService.updateStatus(disputeId, request, currentUser(authentication)));
    }

    @PostMapping("/admin/{disputeId}/notes")
    public ResponseEntity<TransactionDisputeResponse> addNote(
            @PathVariable String disputeId,
            @Valid @RequestBody DisputeNoteRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(disputeService.addNote(disputeId, request, currentUser(authentication)));
    }

    private String currentUser(Authentication authentication) {
        return authentication != null ? authentication.getName() : "SYSTEM";
    }
}
