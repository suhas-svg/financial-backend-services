package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.AuditSummaryResponse;
import com.suhasan.finance.transaction_service.repository.AuditLogEntryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditQueryServiceTest {
    @Test
    void summaryUsesOneBoundedAggregateQuery() {
        AuditLogEntryRepository repository = mock(AuditLogEntryRepository.class);
        AuditQueryService service = new AuditQueryService(repository);
        LocalDateTime from = LocalDateTime.of(2026, 7, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 18, 23, 59);
        AuditSummaryResponse expected = new AuditSummaryResponse(10, 2, 1, 3);
        when(repository.summarize(from, to)).thenReturn(expected);

        assertThat(service.getSummary(from, to)).isEqualTo(expected);
        verify(repository).summarize(from, to);
    }
}