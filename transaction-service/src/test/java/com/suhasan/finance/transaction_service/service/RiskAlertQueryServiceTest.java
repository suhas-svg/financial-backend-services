package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.RiskAlertFilter;
import com.suhasan.finance.transaction_service.dto.RiskAlertStatusUpdateRequest;
import com.suhasan.finance.transaction_service.entity.RiskAlert;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import com.suhasan.finance.transaction_service.repository.RiskAlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskAlertQueryServiceTest {
    private final RiskAlertRepository repository = mock(RiskAlertRepository.class);
    private final RiskAlertQueryService service = new RiskAlertQueryService(repository);

    @Test
    void searchesAndMapsCompleteAlerts() {
        var pageable = PageRequest.of(0, 10);
        var alert = alert();
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(alert)));

        var result = service.searchAlerts(RiskAlertFilter.builder().status(RiskAlertStatus.OPEN).build(), pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().getAlertId()).isEqualTo("alert-1");
        assertThat(result.getContent().getFirst().getAmount()).isEqualByComparingTo("12.50");
    }

    @Test
    void getsUpdatesAndRejectsInvalidStatuses() {
        var alert = alert();
        when(repository.findById("alert-1")).thenReturn(Optional.of(alert));
        when(repository.save(alert)).thenReturn(alert);
        var request = new RiskAlertStatusUpdateRequest();
        request.setStatus(RiskAlertStatus.REVIEWED);
        request.setResolutionNote("reviewed safely");

        var result = service.updateStatus("alert-1", request, "operator");

        assertThat(result.getStatus()).isEqualTo(RiskAlertStatus.REVIEWED);
        assertThat(result.getReviewedBy()).isEqualTo("operator");
        verify(repository).save(alert);

        var invalid = new RiskAlertStatusUpdateRequest();
        assertThatThrownBy(() -> service.updateStatus("alert-1", invalid, "operator"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Status must");
        invalid.setStatus(RiskAlertStatus.OPEN);
        assertThatThrownBy(() -> service.updateStatus("alert-1", invalid, "operator"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Status must");
    }

    @Test
    void summarizesExplicitAndDefaultWindowsAndRejectsMissingAlerts() {
        LocalDateTime from = LocalDateTime.now().minusDays(2);
        LocalDateTime to = LocalDateTime.now();
        when(repository.countByCreatedAtBetween(from, to)).thenReturn(9L);
        when(repository.countByStatusAndCreatedAtBetween(RiskAlertStatus.OPEN, from, to)).thenReturn(4L);
        when(repository.countBySeverityAndCreatedAtBetween(RiskAlertSeverity.HIGH, from, to)).thenReturn(3L);
        when(repository.countByStatusAndCreatedAtBetween(RiskAlertStatus.ESCALATED, from, to)).thenReturn(2L);

        assertThat(service.getSummary(from, to).totalAlerts()).isEqualTo(9L);
        assertThat(service.getSummary(null, null)).isNotNull();
        assertThatThrownBy(() -> service.getAlert("missing"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing");
    }

    private RiskAlert alert() {
        LocalDateTime now = LocalDateTime.now();
        return RiskAlert.builder().alertId("alert-1").alertType(RiskAlertType.RAPID_TRANSFERS)
                .severity(RiskAlertSeverity.HIGH).status(RiskAlertStatus.OPEN).userId("user-1")
                .transactionId("tx-1").fromAccountId("a-1").toAccountId("a-2")
                .amount(new BigDecimal("12.50")).currency("USD").reason("reason")
                .recommendation("review").dedupeKey("dedupe-1").metadata("{}")
                .createdAt(now).updatedAt(now).build();
    }
}
