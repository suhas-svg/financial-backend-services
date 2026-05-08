package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.AuditEventFilter;
import com.suhasan.finance.transaction_service.dto.AuditLogEntryResponse;
import com.suhasan.finance.transaction_service.dto.AuditSummaryResponse;
import com.suhasan.finance.transaction_service.entity.AuditLogEntry;
import com.suhasan.finance.transaction_service.repository.AuditLogEntryRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogEntryRepository repository;

    public Page<AuditLogEntryResponse> searchEvents(AuditEventFilter filter, Pageable pageable) {
        return repository.findAll(toSpecification(filter), pageable)
                .map(this::toResponse);
    }

    public AuditLogEntryResponse getEvent(String eventId) {
        return repository.findById(eventId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Audit event not found: " + eventId));
    }

    public AuditSummaryResponse getSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = from != null ? from : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveTo = to != null ? to : LocalDateTime.now();
        return new AuditSummaryResponse(
                repository.countByCreatedAtBetween(effectiveFrom, effectiveTo),
                repository.countByOutcomeAndCreatedAtBetween("FAILURE", effectiveFrom, effectiveTo),
                repository.countByActionAndCreatedAtBetween("TRANSACTION_REVERSED", effectiveFrom, effectiveTo),
                repository.countByEventTypeAndCreatedAtBetween("SECURITY", effectiveFrom, effectiveTo)
        );
    }

    private Specification<AuditLogEntry> toSpecification(AuditEventFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(filter.getEventType())) {
                predicates.add(criteriaBuilder.equal(root.get("eventType"), filter.getEventType()));
            }
            if (hasText(filter.getAction())) {
                predicates.add(criteriaBuilder.equal(root.get("action"), filter.getAction()));
            }
            if (hasText(filter.getOutcome())) {
                predicates.add(criteriaBuilder.equal(root.get("outcome"), filter.getOutcome()));
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

    private AuditLogEntryResponse toResponse(AuditLogEntry entry) {
        return AuditLogEntryResponse.builder()
                .eventId(entry.getEventId())
                .eventType(entry.getEventType())
                .action(entry.getAction())
                .outcome(entry.getOutcome())
                .userId(entry.getUserId())
                .transactionId(entry.getTransactionId())
                .fromAccountId(entry.getFromAccountId())
                .toAccountId(entry.getToAccountId())
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .ipAddress(entry.getIpAddress())
                .details(entry.getDetails())
                .errorCode(entry.getErrorCode())
                .errorMessage(entry.getErrorMessage())
                .createdAt(entry.getCreatedAt())
                .metadata(entry.getMetadata())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
