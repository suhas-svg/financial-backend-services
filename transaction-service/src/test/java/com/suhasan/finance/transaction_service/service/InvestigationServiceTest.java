package com.suhasan.finance.transaction_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.InvestigationFilter;
import com.suhasan.finance.transaction_service.dto.InvestigationSummaryResponse;
import com.suhasan.finance.transaction_service.dto.InvestigationTimelineItemResponse;
import com.suhasan.finance.transaction_service.entity.AuditLogEntry;
import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import com.suhasan.finance.transaction_service.entity.RiskCase;
import com.suhasan.finance.transaction_service.entity.RiskCaseNote;
import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.DisputeReasonCode;
import com.suhasan.finance.transaction_service.entity.DisputeStatus;
import com.suhasan.finance.transaction_service.entity.TransactionDispute;
import com.suhasan.finance.transaction_service.entity.TransactionDisputeNote;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.repository.AuditLogEntryRepository;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import com.suhasan.finance.transaction_service.repository.RiskCaseRepository;
import com.suhasan.finance.transaction_service.repository.TransactionDisputeRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditLogEntryRepository auditLogEntryRepository;

    @Mock
    private RiskAlertRepository riskAlertRepository;

    @Mock
    private RiskCaseRepository riskCaseRepository;

    @Mock
    private TransactionDisputeRepository disputeRepository;

    private InvestigationService investigationService;

    @BeforeEach
    void setUp() {
        investigationService = new InvestigationService(
                transactionRepository,
                auditLogEntryRepository,
                riskAlertRepository,
                riskCaseRepository,
                disputeRepository,
                new ObjectMapper());
    }

    @Test
    void getTimeline_ExpandsCaseSearchAndReturnsMixedItemsNewestFirst() {
        RiskAlert alert = alert();
        RiskCase riskCase = riskCase(alert);
        when(riskCaseRepository.findById("case-1")).thenReturn(Optional.of(riskCase));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(transaction()));
        when(auditLogEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(audit()));
        when(riskAlertRepository.findAll(any(Specification.class))).thenReturn(List.of(alert));
        when(riskCaseRepository.findAll(any(Specification.class))).thenReturn(List.of(riskCase));
        when(disputeRepository.findAll(any(Specification.class))).thenReturn(List.of(dispute()));

        Page<InvestigationTimelineItemResponse> result = investigationService.getTimeline(
                InvestigationFilter.builder().caseId("case-1").build(),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getTotalElements()).isEqualTo(7);
        assertThat(result.getContent()).extracting(InvestigationTimelineItemResponse::getItemType)
                .containsExactly("CASE_NOTE", "DISPUTE_NOTE", "RISK_CASE", "DISPUTE", "RISK_ALERT", "AUDIT_EVENT", "TRANSACTION");
        assertThat(result.getContent().get(0).getCaseId()).isEqualTo("case-1");
        assertThat(result.getContent().get(3).getTransactionId()).isEqualTo("txn-1");
        assertThat(result.getContent().get(4).getAlertId()).isEqualTo("alert-1");

        ArgumentCaptor<Specification<Transaction>> transactionSpec = ArgumentCaptor.forClass(Specification.class);
        verify(transactionRepository).findAll(transactionSpec.capture());
    }

    @Test
    void getTimeline_EmptySearchReturnsEmptyPage() {
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(auditLogEntryRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(riskAlertRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(riskCaseRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(disputeRepository.findAll(any(Specification.class))).thenReturn(List.of());

        Page<InvestigationTimelineItemResponse> result = investigationService.getTimeline(
                InvestigationFilter.builder().userId("missing").build(),
                PageRequest.of(0, 50));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void getSummary_CountsMixedSourcesAndHighSeverityItems() {
        RiskAlert alert = alert();
        RiskCase riskCase = riskCase(alert);
        when(riskAlertRepository.findById("alert-1")).thenReturn(Optional.of(alert));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(transaction(), reversal()));
        when(auditLogEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(audit(), failedAudit()));
        when(riskAlertRepository.findAll(any(Specification.class))).thenReturn(List.of(alert));
        when(riskCaseRepository.findAll(any(Specification.class))).thenReturn(List.of(riskCase));
        when(disputeRepository.findAll(any(Specification.class))).thenReturn(List.of(dispute()));

        InvestigationSummaryResponse summary = investigationService.getSummary(
                InvestigationFilter.builder().alertId("alert-1").build());

        assertThat(summary.getTransactions()).isEqualTo(2);
        assertThat(summary.getAuditEvents()).isEqualTo(2);
        assertThat(summary.getRiskAlerts()).isEqualTo(1);
        assertThat(summary.getRiskCases()).isEqualTo(1);
        assertThat(summary.getDisputes()).isEqualTo(1);
        assertThat(summary.getDisputeNotes()).isEqualTo(1);
        assertThat(summary.getFailures()).isEqualTo(2);
        assertThat(summary.getReversals()).isEqualTo(1);
        assertThat(summary.getHighSeverityItems()).isEqualTo(2);
    }

    @Test
    void exportTimelineCsv_ReturnsHeaderAndEscapedRows() {
        RiskAlert alert = alert();
        RiskCase riskCase = riskCase(alert);
        when(riskCaseRepository.findById("case-1")).thenReturn(Optional.of(riskCase));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(transactionWithCsvCharacters()));
        when(auditLogEntryRepository.findAll(any(Specification.class))).thenReturn(List.of(audit()));
        when(riskAlertRepository.findAll(any(Specification.class))).thenReturn(List.of(alert));
        when(riskCaseRepository.findAll(any(Specification.class))).thenReturn(List.of(riskCase));
        when(disputeRepository.findAll(any(Specification.class))).thenReturn(List.of(dispute()));

        String csv = investigationService.exportTimelineCsv(
                InvestigationFilter.builder().caseId("case-1").build());

        assertThat(csv).startsWith("itemId,itemType,title,description,severity,status,userId,transactionId,accountId,alertId,caseId,amount,currency,createdAt,metadataJson\n");
        assertThat(csv).contains("txn-csv,TRANSACTION,TRANSFER COMPLETED,\"High value, transfer\"");
        assertThat(csv).contains("audit-1,AUDIT_EVENT");
        assertThat(csv).contains("alert-1,RISK_ALERT");
        assertThat(csv).contains("case-1,RISK_CASE");
        assertThat(csv).contains("CASE_NOTE");
        assertThat(csv).contains("dispute-1,DISPUTE");
        assertThat(csv).contains("dispute-note-1,DISPUTE_NOTE");
        assertThat(csv).contains("\"{\"\"type\"\":\"\"TRANSFER\"\"");
    }

    @Test
    void exportTimelineCsv_NoCriteriaReturnsHeaderOnly() {
        String csv = investigationService.exportTimelineCsv(InvestigationFilter.builder().build());

        assertThat(csv).isEqualTo("itemId,itemType,title,description,severity,status,userId,transactionId,accountId,alertId,caseId,amount,currency,createdAt,metadataJson\n");
    }

    private Transaction transaction() {
        return Transaction.builder()
                .transactionId("txn-1")
                .fromAccountId("101")
                .toAccountId("202")
                .amount(new BigDecimal("6000.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("High value transfer")
                .createdBy("customer")
                .createdAt(LocalDateTime.parse("2026-05-10T10:00:00"))
                .build();
    }

    private Transaction transactionWithCsvCharacters() {
        return Transaction.builder()
                .transactionId("txn-csv")
                .fromAccountId("101")
                .toAccountId("202")
                .amount(new BigDecimal("6000.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("High value, transfer")
                .createdBy("customer")
                .createdAt(LocalDateTime.parse("2026-05-10T10:00:00"))
                .build();
    }

    private Transaction reversal() {
        return Transaction.builder()
                .transactionId("txn-2")
                .fromAccountId("202")
                .toAccountId("101")
                .amount(new BigDecimal("6000.00"))
                .currency("USD")
                .type(TransactionType.REVERSAL)
                .status(TransactionStatus.FAILED)
                .createdBy("customer")
                .createdAt(LocalDateTime.parse("2026-05-10T10:05:00"))
                .build();
    }

    private AuditLogEntry audit() {
        return AuditLogEntry.builder()
                .eventId("audit-1")
                .eventType("TRANSACTION_COMPLETED")
                .action("TRANSFER")
                .outcome("SUCCESS")
                .userId("customer")
                .transactionId("txn-1")
                .fromAccountId("101")
                .toAccountId("202")
                .amount(new BigDecimal("6000.00"))
                .currency("USD")
                .details("Transfer completed")
                .createdAt(LocalDateTime.parse("2026-05-10T10:01:00"))
                .build();
    }

    private AuditLogEntry failedAudit() {
        return AuditLogEntry.builder()
                .eventId("audit-2")
                .eventType("TRANSACTION_FAILED")
                .action("REVERSAL")
                .outcome("FAILURE")
                .userId("customer")
                .transactionId("txn-2")
                .fromAccountId("202")
                .toAccountId("101")
                .details("Reversal failed")
                .createdAt(LocalDateTime.parse("2026-05-10T10:06:00"))
                .build();
    }

    private RiskAlert alert() {
        return RiskAlert.builder()
                .alertId("alert-1")
                .alertType(RiskAlertType.HIGH_VALUE_TRANSFER)
                .severity(RiskAlertSeverity.HIGH)
                .status(RiskAlertStatus.OPEN)
                .userId("customer")
                .transactionId("txn-1")
                .fromAccountId("101")
                .toAccountId("202")
                .amount(new BigDecimal("6000.00"))
                .currency("USD")
                .reason("Transfer amount exceeded threshold")
                .recommendation("Review customer activity")
                .dedupeKey("HIGH_VALUE_TRANSFER:txn-1")
                .createdAt(LocalDateTime.parse("2026-05-10T10:02:00"))
                .updatedAt(LocalDateTime.parse("2026-05-10T10:02:00"))
                .build();
    }

    private RiskCase riskCase(RiskAlert alert) {
        RiskCase riskCase = RiskCase.builder()
                .caseId("case-1")
                .caseNumber("RC-20260510-0001")
                .status(RiskCaseStatus.OPEN)
                .priority(RiskCasePriority.HIGH)
                .title("Review high value transfer")
                .userId("customer")
                .transactionId("txn-1")
                .primaryAlertId("alert-1")
                .createdBy("ops")
                .createdAt(LocalDateTime.parse("2026-05-10T10:03:00"))
                .updatedAt(LocalDateTime.parse("2026-05-10T10:03:00"))
                .linkedAlerts(List.of(alert))
                .build();
        RiskCaseNote note = RiskCaseNote.builder()
                .noteId("note-1")
                .riskCase(riskCase)
                .author("ops")
                .note("Initial review note")
                .createdAt(LocalDateTime.parse("2026-05-10T10:04:00"))
                .build();
        riskCase.setNotes(List.of(note));
        return riskCase;
    }

    private TransactionDispute dispute() {
        TransactionDispute dispute = TransactionDispute.builder()
                .disputeId("dispute-1")
                .disputeNumber("DP-20260510-0001")
                .transactionId("txn-1")
                .userId("customer")
                .status(DisputeStatus.IN_REVIEW)
                .reasonCode(DisputeReasonCode.UNAUTHORIZED)
                .description("Customer reported unauthorized transfer")
                .assignedTo("ops")
                .createdBy("customer")
                .createdAt(LocalDateTime.parse("2026-05-10T10:03:00"))
                .updatedAt(LocalDateTime.parse("2026-05-10T10:03:00"))
                .notes(new ArrayList<>())
                .build();
        dispute.getNotes().add(TransactionDisputeNote.builder()
                .noteId("dispute-note-1")
                .dispute(dispute)
                .author("ops")
                .note("Requested customer confirmation")
                .createdAt(LocalDateTime.parse("2026-05-10T10:04:00"))
                .build());
        return dispute;
    }
}
