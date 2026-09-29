package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyTransactionStatsServiceTest {
    @Mock TransactionRepository transactionRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T12:30:00Z"), ZoneOffset.UTC);

    @Test
    void summarizesTodayFromTheDatabaseNotProcessMemory() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 29, 0, 0);
        when(transactionRepository.summarizeByStatusBetween(start, start.plusDays(1))).thenReturn(List.of(
                new Object[]{TransactionStatus.COMPLETED, 3L, new BigDecimal("3450.00")},
                new Object[]{TransactionStatus.REVERSED, 1L, new BigDecimal("200.00")},
                new Object[]{TransactionStatus.FAILED, 1L, new BigDecimal("10.00")},
                new Object[]{TransactionStatus.PENDING, 2L, new BigDecimal("99.00")}));

        var stats = new DailyTransactionStatsService(transactionRepository, clock).today();

        assertThat(stats.volume()).isEqualTo(4L);
        assertThat(stats.amount()).isEqualByComparingTo("3650.00");
        assertThat(stats.successRate()).isEqualTo(0.8d);
        verify(transactionRepository).summarizeByStatusBetween(start, start.plusDays(1));
    }

    @Test
    void reportsFullSuccessWhenNothingTerminatedToday() {
        when(transactionRepository.summarizeByStatusBetween(
                LocalDateTime.of(2026, 9, 29, 0, 0), LocalDateTime.of(2026, 9, 30, 0, 0))).thenReturn(List.of());

        var stats = new DailyTransactionStatsService(transactionRepository, clock).today();

        assertThat(stats.volume()).isZero();
        assertThat(stats.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.successRate()).isEqualTo(1.0d);
    }
}
