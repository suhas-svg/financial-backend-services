package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily transaction statistics read from the transactions table.
 *
 * The in-memory Micrometer gauges in {@link MetricsService} reset on every restart and are
 * per-replica, so dashboards that must show "today" read the shared database instead.
 * The day window uses the same JVM clock that stamps {@code Transaction.createdAt}.
 */
@Service
public class DailyTransactionStatsService {

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public DailyTransactionStatsService(TransactionRepository transactionRepository) {
        // Transaction.createdAt is stamped with LocalDateTime.now(), i.e. the JVM default zone.
        this(transactionRepository, Clock.systemDefaultZone());
    }

    DailyTransactionStatsService(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public record DailyTransactionStats(long volume, BigDecimal amount, double successRate) {}

    @Transactional(readOnly = true)
    public DailyTransactionStats today() {
        LocalDateTime start = LocalDate.now(clock).atStartOfDay();
        List<Object[]> rows = transactionRepository.summarizeByStatusBetween(start, start.plusDays(1));
        long completed = 0;
        long failed = 0;
        BigDecimal completedAmount = BigDecimal.ZERO;
        for (Object[] row : rows) {
            TransactionStatus status = (TransactionStatus) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal amount = row[2] instanceof BigDecimal value ? value : new BigDecimal(row[2].toString());
            if (status == TransactionStatus.COMPLETED || status == TransactionStatus.REVERSED) {
                // A reversed transaction completed first; its reversal is a separate COMPLETED row.
                completed += count;
                completedAmount = completedAmount.add(amount);
            } else if (status == TransactionStatus.FAILED
                    || status == TransactionStatus.FAILED_REQUIRES_MANUAL_ACTION) {
                failed += count;
            }
        }
        long terminal = completed + failed;
        double successRate = terminal == 0 ? 1.0d : (double) completed / terminal;
        return new DailyTransactionStats(completed, completedAmount, successRate);
    }
}
