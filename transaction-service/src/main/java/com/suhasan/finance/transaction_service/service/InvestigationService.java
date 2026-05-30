package com.suhasan.finance.transaction_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.InvestigationFilter;
import com.suhasan.finance.transaction_service.dto.InvestigationSummaryResponse;
import com.suhasan.finance.transaction_service.dto.InvestigationTimelineItemResponse;
import com.suhasan.finance.transaction_service.entity.AuditLogEntry;
import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskCase;
import com.suhasan.finance.transaction_service.entity.RiskCaseNote;
import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionDispute;
import com.suhasan.finance.transaction_service.entity.TransactionDisputeNote;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.AuditLogEntryRepository;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import com.suhasan.finance.transaction_service.repository.RiskCaseRepository;
import com.suhasan.finance.transaction_service.repository.TransactionDisputeRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InvestigationService {

    private static final int EXPORT_LIMIT = 10_000;
    private static final String CSV_HEADER = "itemId,itemType,title,description,severity,status,userId,transactionId,accountId,alertId,caseId,amount,currency,createdAt,metadataJson\n";

    private final TransactionRepository transactionRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final RiskCaseRepository riskCaseRepository;
    private final TransactionDisputeRepository disputeRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<InvestigationTimelineItemResponse> getTimeline(InvestigationFilter filter, Pageable pageable) {
        InvestigationContext context = resolveContext(filter);
        if (!context.hasCriteria()) {
            return Page.empty(pageable);
        }
        List<InvestigationTimelineItemResponse> items = collectItems(context);
        items.sort(Comparator.comparing(InvestigationTimelineItemResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        int start = (int) Math.min(pageable.getOffset(), items.size());
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    @Transactional(readOnly = true)
    public InvestigationSummaryResponse getSummary(InvestigationFilter filter) {
        InvestigationContext context = resolveContext(filter);
        if (!context.hasCriteria()) {
            return new InvestigationSummaryResponse(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        List<Transaction> transactions = transactionRepository.findAll(transactionSpec(context));
        List<AuditLogEntry> audits = auditLogEntryRepository.findAll(auditSpec(context));
        List<RiskAlert> alerts = riskAlertRepository.findAll(alertSpec(context));
        List<RiskCase> cases = riskCaseRepository.findAll(caseSpec(context));
        List<TransactionDispute> disputes = disputeRepository.findAll(disputeSpec(context));

        long failures = transactions.stream().filter(transaction -> transaction.getStatus() == TransactionStatus.FAILED).count()
                + audits.stream().filter(audit -> "FAILURE".equalsIgnoreCase(audit.getOutcome())).count();
        long reversals = transactions.stream().filter(transaction -> transaction.getType() == TransactionType.REVERSAL).count();
        long highSeverity = alerts.stream().filter(alert -> alert.getSeverity() == RiskAlertSeverity.HIGH).count()
                + cases.stream().filter(riskCase -> riskCase.getPriority() == RiskCasePriority.HIGH || riskCase.getPriority() == RiskCasePriority.CRITICAL).count();
        long disputeNotes = disputes.stream().mapToLong(dispute -> dispute.getNotes().size()).sum();

        return new InvestigationSummaryResponse(transactions.size(), audits.size(), alerts.size(), cases.size(), disputes.size(), disputeNotes, failures, reversals, highSeverity);
    }

    @Transactional(readOnly = true)
    public String exportTimelineCsv(InvestigationFilter filter) {
        Page<InvestigationTimelineItemResponse> page = getTimeline(
                filter,
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")));

        StringBuilder csv = new StringBuilder(CSV_HEADER);
        page.getContent().forEach(item -> csv.append(toCsvRow(item)));
        return csv.toString();
    }

    private List<InvestigationTimelineItemResponse> collectItems(InvestigationContext context) {
        List<InvestigationTimelineItemResponse> items = new ArrayList<>();
        transactionRepository.findAll(transactionSpec(context)).forEach(transaction -> items.add(toTransactionItem(transaction)));
        auditLogEntryRepository.findAll(auditSpec(context)).forEach(audit -> items.add(toAuditItem(audit)));
        riskAlertRepository.findAll(alertSpec(context)).forEach(alert -> items.add(toAlertItem(alert)));
        riskCaseRepository.findAll(caseSpec(context)).forEach(riskCase -> {
            items.add(toCaseItem(riskCase));
            riskCase.getNotes().forEach(note -> items.add(toCaseNoteItem(riskCase, note)));
        });
        disputeRepository.findAll(disputeSpec(context)).forEach(dispute -> {
            items.add(toDisputeItem(dispute));
            dispute.getNotes().forEach(note -> items.add(toDisputeNoteItem(dispute, note)));
        });
        return items;
    }

    private InvestigationContext resolveContext(InvestigationFilter filter) {
        InvestigationContext context = new InvestigationContext(filter);
        if (hasText(filter.getCaseId())) {
            riskCaseRepository.findById(filter.getCaseId()).ifPresent(riskCase -> {
                context.caseIds.add(riskCase.getCaseId());
                addIfPresent(context.userIds, riskCase.getUserId());
                addIfPresent(context.transactionIds, riskCase.getTransactionId());
                addIfPresent(context.alertIds, riskCase.getPrimaryAlertId());
                riskCase.getLinkedAlerts().forEach(alert -> addIfPresent(context.alertIds, alert.getAlertId()));
            });
        }
        if (hasText(filter.getAlertId())) {
            riskAlertRepository.findById(filter.getAlertId()).ifPresent(alert -> {
                context.alertIds.add(alert.getAlertId());
                addIfPresent(context.userIds, alert.getUserId());
                addIfPresent(context.transactionIds, alert.getTransactionId());
                addIfPresent(context.accountIds, alert.getFromAccountId());
                addIfPresent(context.accountIds, alert.getToAccountId());
            });
        }
        return context;
    }

    private Specification<Transaction> transactionSpec(InvestigationContext context) {
        return (root, query, cb) -> {
            List<Predicate> criteria = new ArrayList<>();
            if (!context.userIds.isEmpty()) {
                criteria.add(root.get("createdBy").in(context.userIds));
            }
            if (!context.transactionIds.isEmpty()) {
                criteria.add(root.get("transactionId").in(context.transactionIds));
            }
            if (!context.accountIds.isEmpty()) {
                criteria.add(cb.or(root.get("fromAccountId").in(context.accountIds), root.get("toAccountId").in(context.accountIds)));
            }
            return withDateRange(context, anyMatch(criteria, cb), root.get("createdAt"), cb);
        };
    }

    private Specification<AuditLogEntry> auditSpec(InvestigationContext context) {
        return (root, query, cb) -> {
            List<Predicate> criteria = new ArrayList<>();
            if (!context.userIds.isEmpty()) {
                criteria.add(root.get("userId").in(context.userIds));
            }
            if (!context.transactionIds.isEmpty()) {
                criteria.add(root.get("transactionId").in(context.transactionIds));
            }
            if (!context.accountIds.isEmpty()) {
                criteria.add(cb.or(root.get("fromAccountId").in(context.accountIds), root.get("toAccountId").in(context.accountIds)));
            }
            return withDateRange(context, anyMatch(criteria, cb), root.get("createdAt"), cb);
        };
    }

    private Specification<RiskAlert> alertSpec(InvestigationContext context) {
        return (root, query, cb) -> {
            List<Predicate> criteria = new ArrayList<>();
            if (!context.alertIds.isEmpty()) {
                criteria.add(root.get("alertId").in(context.alertIds));
            }
            if (!context.userIds.isEmpty()) {
                criteria.add(root.get("userId").in(context.userIds));
            }
            if (!context.transactionIds.isEmpty()) {
                criteria.add(root.get("transactionId").in(context.transactionIds));
            }
            if (!context.accountIds.isEmpty()) {
                criteria.add(cb.or(root.get("fromAccountId").in(context.accountIds), root.get("toAccountId").in(context.accountIds)));
            }
            return withDateRange(context, anyMatch(criteria, cb), root.get("createdAt"), cb);
        };
    }

    private Specification<RiskCase> caseSpec(InvestigationContext context) {
        return (root, query, cb) -> {
            List<Predicate> criteria = new ArrayList<>();
            if (!context.caseIds.isEmpty()) {
                criteria.add(root.get("caseId").in(context.caseIds));
            }
            if (!context.alertIds.isEmpty()) {
                criteria.add(root.get("primaryAlertId").in(context.alertIds));
            }
            if (!context.userIds.isEmpty()) {
                criteria.add(root.get("userId").in(context.userIds));
            }
            if (!context.transactionIds.isEmpty()) {
                criteria.add(root.get("transactionId").in(context.transactionIds));
            }
            return withDateRange(context, anyMatch(criteria, cb), root.get("createdAt"), cb);
        };
    }

    private Specification<TransactionDispute> disputeSpec(InvestigationContext context) {
        return (root, query, cb) -> {
            List<Predicate> criteria = new ArrayList<>();
            if (!context.userIds.isEmpty()) {
                criteria.add(root.get("userId").in(context.userIds));
            }
            if (!context.transactionIds.isEmpty()) {
                criteria.add(root.get("transactionId").in(context.transactionIds));
            }
            return withDateRange(context, anyMatch(criteria, cb), root.get("createdAt"), cb);
        };
    }

    private Predicate anyMatch(List<Predicate> criteria, jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (criteria.isEmpty()) {
            return cb.disjunction();
        }
        return cb.or(criteria.toArray(Predicate[]::new));
    }

    private Predicate withDateRange(InvestigationContext context, Predicate criteria, jakarta.persistence.criteria.Path<LocalDateTime> path,
                                    jakarta.persistence.criteria.CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteria);
        if (context.from != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, context.from));
        }
        if (context.to != null) {
            predicates.add(cb.lessThanOrEqualTo(path, context.to));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    private InvestigationTimelineItemResponse toTransactionItem(Transaction transaction) {
        return InvestigationTimelineItemResponse.builder()
                .itemId(transaction.getTransactionId())
                .itemType("TRANSACTION")
                .title(transaction.getType() + " " + transaction.getStatus())
                .description(transaction.getDescription())
                .status(name(transaction.getStatus()))
                .userId(transaction.getCreatedBy())
                .transactionId(transaction.getTransactionId())
                .accountId(firstNonBlank(transaction.getFromAccountId(), transaction.getToAccountId()))
                .amount(amount(transaction.getAmount()))
                .currency(transaction.getCurrency())
                .createdAt(transaction.getCreatedAt())
                .metadata(metadata(
                        "type", name(transaction.getType()),
                        "fromAccountId", transaction.getFromAccountId(),
                        "toAccountId", transaction.getToAccountId(),
                        "reference", transaction.getReference(),
                        "processingState", name(transaction.getProcessingState())))
                .build();
    }

    private InvestigationTimelineItemResponse toAuditItem(AuditLogEntry audit) {
        return InvestigationTimelineItemResponse.builder()
                .itemId(audit.getEventId())
                .itemType("AUDIT_EVENT")
                .title(audit.getAction() + " " + audit.getOutcome())
                .description(firstNonBlank(audit.getDetails(), audit.getErrorMessage()))
                .status(audit.getOutcome())
                .userId(audit.getUserId())
                .transactionId(audit.getTransactionId())
                .accountId(firstNonBlank(audit.getFromAccountId(), audit.getToAccountId()))
                .amount(amount(audit.getAmount()))
                .currency(audit.getCurrency())
                .createdAt(audit.getCreatedAt())
                .metadata(metadata(
                        "eventType", audit.getEventType(),
                        "errorCode", audit.getErrorCode(),
                        "errorMessage", audit.getErrorMessage(),
                        "metadata", audit.getMetadata()))
                .build();
    }

    private InvestigationTimelineItemResponse toAlertItem(RiskAlert alert) {
        return InvestigationTimelineItemResponse.builder()
                .itemId(alert.getAlertId())
                .itemType("RISK_ALERT")
                .title(alert.getAlertType().name())
                .description(alert.getReason())
                .severity(name(alert.getSeverity()))
                .status(name(alert.getStatus()))
                .userId(alert.getUserId())
                .transactionId(alert.getTransactionId())
                .accountId(firstNonBlank(alert.getFromAccountId(), alert.getToAccountId()))
                .alertId(alert.getAlertId())
                .amount(amount(alert.getAmount()))
                .currency(alert.getCurrency())
                .createdAt(alert.getCreatedAt())
                .metadata(metadata(
                        "recommendation", alert.getRecommendation(),
                        "dedupeKey", alert.getDedupeKey(),
                        "metadata", alert.getMetadata()))
                .build();
    }

    private InvestigationTimelineItemResponse toCaseItem(RiskCase riskCase) {
        return InvestigationTimelineItemResponse.builder()
                .itemId(riskCase.getCaseId())
                .itemType("RISK_CASE")
                .title(riskCase.getCaseNumber())
                .description(riskCase.getTitle())
                .severity(name(riskCase.getPriority()))
                .status(name(riskCase.getStatus()))
                .userId(riskCase.getUserId())
                .transactionId(riskCase.getTransactionId())
                .alertId(riskCase.getPrimaryAlertId())
                .caseId(riskCase.getCaseId())
                .createdAt(riskCase.getCreatedAt())
                .metadata(metadata(
                        "assignedTo", riskCase.getAssignedTo(),
                        "createdBy", riskCase.getCreatedBy(),
                        "resolutionNote", riskCase.getResolutionNote()))
                .build();
    }

    private InvestigationTimelineItemResponse toCaseNoteItem(RiskCase riskCase, RiskCaseNote note) {
        return InvestigationTimelineItemResponse.builder()
                .itemId(note.getNoteId())
                .itemType("CASE_NOTE")
                .title("Case note")
                .description(note.getNote())
                .userId(riskCase.getUserId())
                .transactionId(riskCase.getTransactionId())
                .alertId(riskCase.getPrimaryAlertId())
                .caseId(riskCase.getCaseId())
                .createdAt(note.getCreatedAt())
                .metadata(metadata("author", note.getAuthor(), "caseNumber", riskCase.getCaseNumber()))
                .build();
    }

    private InvestigationTimelineItemResponse toDisputeItem(TransactionDispute dispute) {
        return InvestigationTimelineItemResponse.builder()
                .itemId(dispute.getDisputeId())
                .itemType("DISPUTE")
                .title(dispute.getDisputeNumber())
                .description(dispute.getDescription())
                .severity(name(dispute.getReasonCode()))
                .status(name(dispute.getStatus()))
                .userId(dispute.getUserId())
                .transactionId(dispute.getTransactionId())
                .createdAt(dispute.getCreatedAt())
                .metadata(metadata(
                        "assignedTo", dispute.getAssignedTo(),
                        "createdBy", dispute.getCreatedBy(),
                        "resolutionNote", dispute.getResolutionNote()))
                .build();
    }

    private InvestigationTimelineItemResponse toDisputeNoteItem(TransactionDispute dispute, TransactionDisputeNote note) {
        return InvestigationTimelineItemResponse.builder()
                .itemId(note.getNoteId())
                .itemType("DISPUTE_NOTE")
                .title("Dispute note")
                .description(note.getNote())
                .userId(dispute.getUserId())
                .transactionId(dispute.getTransactionId())
                .createdAt(note.getCreatedAt())
                .metadata(metadata("author", note.getAuthor(), "disputeNumber", dispute.getDisputeNumber()))
                .build();
    }

    private Map<String, Object> metadata(Object... values) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] != null) {
                metadata.put(String.valueOf(values[i]), values[i + 1]);
            }
        }
        return metadata;
    }

    private String amount(BigDecimal amount) {
        return amount != null ? amount.toPlainString() : null;
    }

    private String name(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private void addIfPresent(Set<String> values, String value) {
        if (hasText(value)) {
            values.add(value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toCsvRow(InvestigationTimelineItemResponse item) {
        return String.join(",",
                csv(item.getItemId()),
                csv(item.getItemType()),
                csv(item.getTitle()),
                csv(item.getDescription()),
                csv(item.getSeverity()),
                csv(item.getStatus()),
                csv(item.getUserId()),
                csv(item.getTransactionId()),
                csv(item.getAccountId()),
                csv(item.getAlertId()),
                csv(item.getCaseId()),
                csv(item.getAmount()),
                csv(item.getCurrency()),
                csv(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null),
                csv(metadataJson(item))) + "\n";
    }

    private String metadataJson(InvestigationTimelineItemResponse item) {
        try {
            return objectMapper.writeValueAsString(item.getMetadata() != null ? item.getMetadata() : Map.of());
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private class InvestigationContext {
        private final Set<String> userIds = new HashSet<>();
        private final Set<String> transactionIds = new HashSet<>();
        private final Set<String> accountIds = new HashSet<>();
        private final Set<String> alertIds = new HashSet<>();
        private final Set<String> caseIds = new HashSet<>();
        private final LocalDateTime from;
        private final LocalDateTime to;

        private InvestigationContext(InvestigationFilter filter) {
            addIfPresent(userIds, filter.getUserId());
            addIfPresent(transactionIds, filter.getTransactionId());
            addIfPresent(accountIds, filter.getAccountId());
            addIfPresent(alertIds, filter.getAlertId());
            addIfPresent(caseIds, filter.getCaseId());
            this.from = filter.getFrom();
            this.to = filter.getTo();
        }

        private boolean hasCriteria() {
            return !userIds.isEmpty() || !transactionIds.isEmpty() || !accountIds.isEmpty() || !alertIds.isEmpty() || !caseIds.isEmpty();
        }
    }
}
