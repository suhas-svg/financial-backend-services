package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.RiskAlertFilter;
import com.suhasan.finance.transaction_service.dto.RiskAlertResponse;
import com.suhasan.finance.transaction_service.dto.RiskAlertStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.RiskSummaryResponse;
import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskAlertQueryService {

    private final RiskAlertRepository repository;

    public Page<RiskAlertResponse> searchAlerts(RiskAlertFilter filter, Pageable pageable) {
        return repository.findAll(toSpecification(filter), pageable)
                .map(this::toResponse);
    }

    public RiskAlertResponse getAlert(String alertId) {
        return repository.findById(alertId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Risk alert not found: " + alertId));
    }

    public RiskSummaryResponse getSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = from != null ? from : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now();
        return new RiskSummaryResponse(
                repository.countByCreatedAtBetween(effectiveFrom, effectiveTo),
                repository.countByStatusAndCreatedAtBetween(RiskAlertStatus.OPEN, effectiveFrom, effectiveTo),
                repository.countBySeverityAndCreatedAtBetween(RiskAlertSeverity.HIGH, effectiveFrom, effectiveTo),
                repository.countByStatusAndCreatedAtBetween(RiskAlertStatus.ESCALATED, effectiveFrom, effectiveTo)
        );
    }

    @Transactional
    public RiskAlertResponse updateStatus(String alertId, RiskAlertStatusUpdateRequest request, String reviewer) {
        if (request.getStatus() == null || request.getStatus() == RiskAlertStatus.OPEN) {
            throw new IllegalArgumentException("Status must be REVIEWED, DISMISSED, or ESCALATED");
        }
        RiskAlert alert = repository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Risk alert not found: " + alertId));
        alert.setStatus(request.getStatus());
        alert.setResolutionNote(request.getResolutionNote());
        alert.setReviewedBy(reviewer);
        alert.setReviewedAt(LocalDateTime.now());
        return toResponse(repository.save(alert));
    }

    private Specification<RiskAlert> toSpecification(RiskAlertFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getSeverity() != null) {
                predicates.add(criteriaBuilder.equal(root.get("severity"), filter.getSeverity()));
            }
            if (filter.getAlertType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("alertType"), filter.getAlertType()));
            }
            if (hasText(filter.getUserId())) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), filter.getUserId()));
            }
            if (hasText(filter.getTransactionId())) {
                predicates.add(criteriaBuilder.equal(root.get("transactionId"), filter.getTransactionId()));
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

    private RiskAlertResponse toResponse(RiskAlert alert) {
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
