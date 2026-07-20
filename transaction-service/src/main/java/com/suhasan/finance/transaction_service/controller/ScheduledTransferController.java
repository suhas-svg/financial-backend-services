package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.ScheduledTransferCreateRequest;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferResponse;
import com.suhasan.finance.transaction_service.dto.ScheduledTransferRunResponse;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus;
import com.suhasan.finance.transaction_service.service.ScheduledTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scheduled-transfers")
@RequiredArgsConstructor
public class ScheduledTransferController {

    private final ScheduledTransferService scheduledTransferService;

    @PostMapping
    public ResponseEntity<ScheduledTransferResponse> create(
            @Valid @RequestBody ScheduledTransferCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduledTransferService.create(request, currentUser(authentication)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ScheduledTransferResponse>> list(
            @RequestParam(required = false) ScheduledTransferStatus status,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "nextRunAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                scheduledTransferService.list(currentUser(authentication), status, pageable)));
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ScheduledTransferResponse> get(
            @PathVariable String scheduleId,
            Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.get(scheduleId, currentUser(authentication)));
    }

    @PatchMapping("/{scheduleId}/pause")
    public ResponseEntity<ScheduledTransferResponse> pause(
            @PathVariable String scheduleId,
            Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.pause(scheduleId, currentUser(authentication)));
    }

    @PatchMapping("/{scheduleId}/resume")
    public ResponseEntity<ScheduledTransferResponse> resume(
            @PathVariable String scheduleId,
            Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.resume(scheduleId, currentUser(authentication)));
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<ScheduledTransferResponse> cancel(
            @PathVariable String scheduleId,
            Authentication authentication) {
        return ResponseEntity.ok(scheduledTransferService.cancel(scheduleId, currentUser(authentication)));
    }

    @GetMapping("/{scheduleId}/runs")
    public ResponseEntity<PageResponse<ScheduledTransferRunResponse>> runs(
            @PathVariable String scheduleId,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "scheduledFor", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                scheduledTransferService.listRuns(scheduleId, currentUser(authentication), pageable)));
    }

    @PostMapping("/admin/recover-stale")
    public ResponseEntity<java.util.Map<String, Integer>> recoverStale(
            @RequestParam(defaultValue = "100") int batchSize,
            Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException("Operator role is required");
        }
        int processed = scheduledTransferService.executeDueTransfers(java.time.Instant.now(), batchSize);
        return ResponseEntity.ok(java.util.Map.of("processed", processed));
    }
    private String currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return authentication.getName();
    }

    public record PageResponse<T>(
            List<T> content,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {

        static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast());
        }
    }
}
