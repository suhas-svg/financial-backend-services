package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.InvestigationFilter;
import com.suhasan.finance.transaction_service.dto.InvestigationSummaryResponse;
import com.suhasan.finance.transaction_service.dto.InvestigationTimelineItemResponse;
import com.suhasan.finance.transaction_service.service.InvestigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/investigations")
@RequiredArgsConstructor
public class InvestigationController {

    private final InvestigationService investigationService;

    @GetMapping("/timeline")
    public ResponseEntity<Page<InvestigationTimelineItemResponse>> getTimeline(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String alertId,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(investigationService.getTimeline(toFilter(userId, transactionId, accountId, alertId, caseId, from, to), pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<InvestigationSummaryResponse> getSummary(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String alertId,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(investigationService.getSummary(toFilter(userId, transactionId, accountId, alertId, caseId, from, to)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportTimeline(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String alertId,
            @RequestParam(required = false) String caseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String csv = investigationService.exportTimelineCsv(toFilter(userId, transactionId, accountId, alertId, caseId, from, to));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"investigation-export.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    private InvestigationFilter toFilter(String userId, String transactionId, String accountId, String alertId, String caseId,
                                         LocalDateTime from, LocalDateTime to) {
        return InvestigationFilter.builder()
                .userId(userId)
                .transactionId(transactionId)
                .accountId(accountId)
                .alertId(alertId)
                .caseId(caseId)
                .from(from)
                .to(to)
                .build();
    }
}
