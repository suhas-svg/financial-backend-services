package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.RiskCaseCreateRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseNoteRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseResponse;
import com.suhasan.finance.transaction_service.dto.RiskCaseStatusUpdateRequest;
import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import com.suhasan.finance.transaction_service.entity.RiskCase;
import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import com.suhasan.finance.transaction_service.repository.RiskCaseNoteRepository;
import com.suhasan.finance.transaction_service.repository.RiskCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskCaseServiceTest {

    @Mock
    private RiskCaseRepository riskCaseRepository;

    @Mock
    private RiskCaseNoteRepository riskCaseNoteRepository;

    @Mock
    private RiskAlertRepository riskAlertRepository;

    @InjectMocks
    private RiskCaseService riskCaseService;

    @Test
    void createFromAlert_CopiesAlertContextAndDefaultsPriorityFromSeverity() {
        RiskAlert alert = highValueAlert();
        when(riskAlertRepository.findById("alert-1")).thenReturn(Optional.of(alert));
        when(riskCaseRepository.findOpenCaseByAlertId("alert-1")).thenReturn(Optional.empty());
        when(riskCaseRepository.countByCreatedAtBetween(any(), any())).thenReturn(7L);
        when(riskCaseRepository.save(any(RiskCase.class))).thenAnswer(invocation -> {
            RiskCase saved = invocation.getArgument(0);
            saved.setCaseId("case-1");
            saved.setCreatedAt(LocalDateTime.parse("2026-05-09T10:30:00"));
            saved.setUpdatedAt(LocalDateTime.parse("2026-05-09T10:30:00"));
            return saved;
        });

        RiskCaseResponse response = riskCaseService.createFromAlert("alert-1",
                RiskCaseCreateRequest.builder().title("Review high-value transfer").build(),
                "ops");

        assertThat(response.getCaseId()).isEqualTo("case-1");
        assertThat(response.getCaseNumber()).isEqualTo("RC-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-0008");
        assertThat(response.getPriority()).isEqualTo(RiskCasePriority.HIGH);
        assertThat(response.getStatus()).isEqualTo(RiskCaseStatus.OPEN);
        assertThat(response.getPrimaryAlertId()).isEqualTo("alert-1");
        assertThat(response.getUserId()).isEqualTo("customer");
        assertThat(response.getTransactionId()).isEqualTo("txn-1");
        assertThat(response.getCreatedBy()).isEqualTo("ops");

        ArgumentCaptor<RiskCase> captor = ArgumentCaptor.forClass(RiskCase.class);
        verify(riskCaseRepository).save(captor.capture());
        assertThat(captor.getValue().getLinkedAlerts()).containsExactly(alert);
    }

    @Test
    void createFromAlert_ReturnsExistingOpenCaseForDuplicateAlert() {
        RiskCase existing = RiskCase.builder()
                .caseId("case-1")
                .caseNumber("RC-20260509-0001")
                .status(RiskCaseStatus.OPEN)
                .priority(RiskCasePriority.HIGH)
                .title("Existing case")
                .primaryAlertId("alert-1")
                .createdAt(LocalDateTime.parse("2026-05-09T10:30:00"))
                .updatedAt(LocalDateTime.parse("2026-05-09T10:30:00"))
                .build();
        when(riskCaseRepository.findOpenCaseByAlertId("alert-1")).thenReturn(Optional.of(existing));

        RiskCaseResponse response = riskCaseService.createFromAlert("alert-1", RiskCaseCreateRequest.builder().build(), "ops");

        assertThat(response.getCaseId()).isEqualTo("case-1");
        assertThat(response.getTitle()).isEqualTo("Existing case");
    }

    @Test
    void claimCase_AssignsCurrentAdminAndMovesOpenCaseToInReview() {
        RiskCase riskCase = RiskCase.builder()
                .caseId("case-1")
                .status(RiskCaseStatus.OPEN)
                .priority(RiskCasePriority.MEDIUM)
                .title("Review alert")
                .createdAt(LocalDateTime.parse("2026-05-09T10:30:00"))
                .updatedAt(LocalDateTime.parse("2026-05-09T10:30:00"))
                .build();
        when(riskCaseRepository.findById("case-1")).thenReturn(Optional.of(riskCase));
        when(riskCaseRepository.save(any(RiskCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RiskCaseResponse response = riskCaseService.claimCase("case-1", "ops");

        assertThat(response.getAssignedTo()).isEqualTo("ops");
        assertThat(response.getStatus()).isEqualTo(RiskCaseStatus.IN_REVIEW);
        assertThat(response.getClaimedAt()).isNotNull();
    }

    @Test
    void updateStatus_ClosesResolvedCasesWithResolutionNote() {
        RiskCase riskCase = RiskCase.builder()
                .caseId("case-1")
                .status(RiskCaseStatus.IN_REVIEW)
                .priority(RiskCasePriority.HIGH)
                .title("Review alert")
                .createdAt(LocalDateTime.parse("2026-05-09T10:30:00"))
                .updatedAt(LocalDateTime.parse("2026-05-09T10:30:00"))
                .build();
        when(riskCaseRepository.findById("case-1")).thenReturn(Optional.of(riskCase));
        when(riskCaseRepository.save(any(RiskCase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RiskCaseResponse response = riskCaseService.updateStatus("case-1",
                new RiskCaseStatusUpdateRequest(RiskCaseStatus.RESOLVED, "Reviewed and resolved"),
                "ops");

        assertThat(response.getStatus()).isEqualTo(RiskCaseStatus.RESOLVED);
        assertThat(response.getResolutionNote()).isEqualTo("Reviewed and resolved");
        assertThat(response.getClosedAt()).isNotNull();
    }

    @Test
    void addNote_RejectsBlankNotes() {
        assertThatThrownBy(() -> riskCaseService.addNote("case-1", new RiskCaseNoteRequest(" "), "ops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Note is required");
    }

    private RiskAlert highValueAlert() {
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
                .reason("Transfer amount exceeded high-value threshold")
                .recommendation("Review sender and recipient")
                .dedupeKey("HIGH_VALUE_TRANSFER:txn-1")
                .createdAt(LocalDateTime.parse("2026-05-09T10:15:30"))
                .updatedAt(LocalDateTime.parse("2026-05-09T10:15:30"))
                .build();
    }
}
