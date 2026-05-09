package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.RiskAlertResponse;
import com.suhasan.finance.transaction_service.dto.RiskCaseCreateRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseFilter;
import com.suhasan.finance.transaction_service.dto.RiskCaseNoteRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseResponse;
import com.suhasan.finance.transaction_service.dto.RiskCaseStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseSummaryResponse;
import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskCase;
import com.suhasan.finance.transaction_service.entity.RiskCaseNote;
import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import com.suhasan.finance.transaction_service.repository.RiskCaseNoteRepository;
import com.suhasan.finance.transaction_service.repository.RiskCaseRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskCaseService {

    private static final DateTimeFormatter CASE_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final RiskCaseRepository riskCaseRepository;
    private final RiskCaseNoteRepository riskCaseNoteRepository;
    private final RiskAlertRepository riskAlertRepository;

    @Transactional(readOnly = true)
    public Page<RiskCaseResponse> searchCases(RiskCaseFilter filter, Pageable pageable) {
        return riskCaseRepository.findAll(toSpecification(filter), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RiskCaseResponse getCase(String caseId) {
        return riskCaseRepository.findById(caseId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Risk case not found: " + caseId));
    }

    @Transactional(readOnly = true)
    public RiskCaseSummaryResponse getSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = from != null ? from : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now();
        return new RiskCaseSummaryResponse(
                riskCaseRepository.countByCreatedAtBetween(effectiveFrom, effectiveTo),
                riskCaseRepository.countByStatusAndCreatedAtBetween(RiskCaseStatus.OPEN, effectiveFrom, effectiveTo),
                riskCaseRepository.countByStatusAndCreatedAtBetween(RiskCaseStatus.IN_REVIEW, effectiveFrom, effectiveTo),
                riskCaseRepository.countByStatusAndCreatedAtBetween(RiskCaseStatus.RESOLVED, effectiveFrom, effectiveTo),
                riskCaseRepository.countByStatusAndCreatedAtBetween(RiskCaseStatus.CLOSED, effectiveFrom, effectiveTo),
                riskCaseRepository.countByAssignedToIsNullAndCreatedAtBetween(effectiveFrom, effectiveTo)
        );
    }

    @Transactional
    public RiskCaseResponse createFromAlert(String alertId, RiskCaseCreateRequest request, String createdBy) {
        return riskCaseRepository.findOpenCaseByAlertId(alertId)
                .map(this::toResponse)
                .orElseGet(() -> createNewCase(alertId, request, createdBy));
    }

    @Transactional
    public RiskCaseResponse claimCase(String caseId, String admin) {
        RiskCase riskCase = findCase(caseId);
        if (hasText(riskCase.getAssignedTo()) && !riskCase.getAssignedTo().equals(admin)) {
            throw new IllegalArgumentException("Risk case is already assigned to " + riskCase.getAssignedTo());
        }
        if (riskCase.getStatus() == RiskCaseStatus.RESOLVED || riskCase.getStatus() == RiskCaseStatus.CLOSED) {
            throw new IllegalArgumentException("Closed risk cases cannot be claimed");
        }
        riskCase.setAssignedTo(admin);
        riskCase.setClaimedAt(LocalDateTime.now());
        if (riskCase.getStatus() == RiskCaseStatus.OPEN) {
            riskCase.setStatus(RiskCaseStatus.IN_REVIEW);
        }
        return toResponse(riskCaseRepository.save(riskCase));
    }

    @Transactional
    public RiskCaseResponse updateStatus(String caseId, RiskCaseStatusUpdateRequest request, String admin) {
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("Case status is required");
        }
        RiskCase riskCase = findCase(caseId);
        riskCase.setStatus(request.getStatus());
        riskCase.setResolutionNote(request.getResolutionNote());
        if ((request.getStatus() == RiskCaseStatus.RESOLVED || request.getStatus() == RiskCaseStatus.CLOSED)
                && riskCase.getClosedAt() == null) {
            riskCase.setClosedAt(LocalDateTime.now());
        }
        if (request.getStatus() == RiskCaseStatus.OPEN || request.getStatus() == RiskCaseStatus.IN_REVIEW) {
            riskCase.setClosedAt(null);
        }
        return toResponse(riskCaseRepository.save(riskCase));
    }

    @Transactional
    public RiskCaseResponse addNote(String caseId, RiskCaseNoteRequest request, String author) {
        if (request == null || !hasText(request.getNote())) {
            throw new IllegalArgumentException("Note is required");
        }
        RiskCase riskCase = findCase(caseId);
        RiskCaseNote note = RiskCaseNote.builder()
                .riskCase(riskCase)
                .author(author)
                .note(request.getNote().trim())
                .build();
        riskCase.getNotes().add(note);
        riskCaseNoteRepository.save(note);
        return toResponse(riskCase);
    }

    private RiskCaseResponse createNewCase(String alertId, RiskCaseCreateRequest request, String createdBy) {
        RiskAlert alert = riskAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Risk alert not found: " + alertId));
        RiskCase riskCase = RiskCase.builder()
                .caseNumber(nextCaseNumber())
                .status(RiskCaseStatus.OPEN)
                .priority(resolvePriority(request, alert))
                .title(resolveTitle(request, alert))
                .userId(alert.getUserId())
                .transactionId(alert.getTransactionId())
                .primaryAlertId(alert.getAlertId())
                .createdBy(createdBy)
                .linkedAlerts(new ArrayList<>(List.of(alert)))
                .build();
        if (request != null && hasText(request.getReason())) {
            RiskCaseNote note = RiskCaseNote.builder()
                    .riskCase(riskCase)
                    .author(createdBy)
                    .note(request.getReason().trim())
                    .build();
            riskCase.getNotes().add(note);
        }
        return toResponse(riskCaseRepository.save(riskCase));
    }

    private String nextCaseNumber() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);
        long sequence = riskCaseRepository.countByCreatedAtBetween(start, end) + 1;
        return "RC-" + today.format(CASE_DAY) + "-" + String.format("%04d", sequence);
    }

    private RiskCasePriority resolvePriority(RiskCaseCreateRequest request, RiskAlert alert) {
        if (request != null && request.getPriority() != null) {
            return request.getPriority();
        }
        if (alert.getSeverity() == RiskAlertSeverity.HIGH) {
            return RiskCasePriority.HIGH;
        }
        if (alert.getSeverity() == RiskAlertSeverity.MEDIUM) {
            return RiskCasePriority.MEDIUM;
        }
        return RiskCasePriority.LOW;
    }

    private String resolveTitle(RiskCaseCreateRequest request, RiskAlert alert) {
        if (request != null && hasText(request.getTitle())) {
            return request.getTitle().trim();
        }
        return "Review " + alert.getAlertType();
    }

    private RiskCase findCase(String caseId) {
        return riskCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Risk case not found: " + caseId));
    }

    private Specification<RiskCase> toSpecification(RiskCaseFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getPriority() != null) {
                predicates.add(criteriaBuilder.equal(root.get("priority"), filter.getPriority()));
            }
            if (hasText(filter.getAssignedTo())) {
                if ("UNASSIGNED".equalsIgnoreCase(filter.getAssignedTo())) {
                    predicates.add(criteriaBuilder.isNull(root.get("assignedTo")));
                } else {
                    predicates.add(criteriaBuilder.equal(root.get("assignedTo"), filter.getAssignedTo()));
                }
            }
            if (hasText(filter.getUserId())) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), filter.getUserId()));
            }
            if (hasText(filter.getTransactionId())) {
                predicates.add(criteriaBuilder.equal(root.get("transactionId"), filter.getTransactionId()));
            }
            if (hasText(filter.getAlertId())) {
                predicates.add(criteriaBuilder.equal(root.get("primaryAlertId"), filter.getAlertId()));
                query.distinct(true);
            }
            if (filter.getFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), filter.getFrom()));
            }
            if (filter.getTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), filter.getTo()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private RiskCaseResponse toResponse(RiskCase riskCase) {
        return RiskCaseResponse.builder()
                .caseId(riskCase.getCaseId())
                .caseNumber(riskCase.getCaseNumber())
                .status(riskCase.getStatus())
                .priority(riskCase.getPriority())
                .title(riskCase.getTitle())
                .userId(riskCase.getUserId())
                .transactionId(riskCase.getTransactionId())
                .primaryAlertId(riskCase.getPrimaryAlertId())
                .assignedTo(riskCase.getAssignedTo())
                .createdBy(riskCase.getCreatedBy())
                .createdAt(riskCase.getCreatedAt())
                .updatedAt(riskCase.getUpdatedAt())
                .claimedAt(riskCase.getClaimedAt())
                .closedAt(riskCase.getClosedAt())
                .resolutionNote(riskCase.getResolutionNote())
                .linkedAlerts(riskCase.getLinkedAlerts().stream().map(this::toAlertResponse).toList())
                .notes(riskCase.getNotes().stream().map(this::toNoteResponse).toList())
                .build();
    }

    private RiskCaseResponse.Note toNoteResponse(RiskCaseNote note) {
        return RiskCaseResponse.Note.builder()
                .noteId(note.getNoteId())
                .author(note.getAuthor())
                .note(note.getNote())
                .createdAt(note.getCreatedAt())
                .build();
    }

    private RiskAlertResponse toAlertResponse(RiskAlert alert) {
        return RiskAlertResponse.builder()
                .alertId(alert.getAlertId())
                .alertType(alert.getAlertType())
                .severity(alert.getSeverity())
                .status(alert.getStatus())
                .userId(alert.getUserId())
                .transactionId(alert.getTransactionId())
                .fromAccountId(alert.getFromAccountId())
                .toAccountId(alert.getToAccountId())
                .amount(alert.getAmount())
                .currency(alert.getCurrency())
                .reason(alert.getReason())
                .recommendation(alert.getRecommendation())
                .dedupeKey(alert.getDedupeKey())
                .metadata(alert.getMetadata())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .reviewedBy(alert.getReviewedBy())
                .reviewedAt(alert.getReviewedAt())
                .resolutionNote(alert.getResolutionNote())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
