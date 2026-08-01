package com.suhasan.finance.transaction_service.operations;

import com.suhasan.finance.transaction_service.ledger.service.LedgerReconciliationService;
import com.suhasan.finance.transaction_service.ledger.service.MonthlyStatementService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class FinancialOperationsCoordinator {
    static final String DAILY = "DAILY_RECONCILIATION";
    static final String MONTHLY = "MONTHLY_STATEMENT_CLOSE";

    private final JdbcTemplate jdbc;
    private final LedgerReconciliationService reconciliation;
    private final MonthlyStatementService statements;
    private final Clock clock;
    private final Counter completed;
    private final Counter failed;
    private final AtomicLong lastSuccessEpoch = new AtomicLong();

    @Value("${financial-operations.enabled:false}") private boolean enabled = false;
    @Value("${financial-operations.zone:UTC}") private String zone = "UTC";
    @Value("${financial-operations.claim-lease-seconds:1800}") private long claimLeaseSeconds = 1800;

    @Autowired
    public FinancialOperationsCoordinator(
            JdbcTemplate jdbc,
            LedgerReconciliationService reconciliation,
            MonthlyStatementService statements,
            MeterRegistry meterRegistry) {
        this(jdbc, reconciliation, statements, meterRegistry, Clock.systemUTC());
    }

    FinancialOperationsCoordinator(
            JdbcTemplate jdbc,
            LedgerReconciliationService reconciliation,
            MonthlyStatementService statements,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.jdbc = jdbc;
        this.reconciliation = reconciliation;
        this.statements = statements;
        this.clock = clock;
        this.completed = meterRegistry.counter("financial.operations.completed");
        this.failed = meterRegistry.counter("financial.operations.failed");
        Gauge.builder("financial.operations.last_success_epoch_seconds", lastSuccessEpoch, AtomicLong::doubleValue)
                .register(meterRegistry);
    }

    @Scheduled(cron = "${financial-operations.daily-cron:0 15 2 * * *}", zone = "${financial-operations.zone:UTC}")
    public void scheduleDaily() {
        if (!enabled) return;
        LocalDate businessDate = LocalDate.now(clock.withZone(ZoneId.of(zone))).minusDays(1);
        safely(() -> runDaily(businessDate, "scheduler", "scheduled daily reconciliation"));
    }

    @Scheduled(cron = "${financial-operations.monthly-cron:0 30 3 1 * *}", zone = "${financial-operations.zone:UTC}")
    public void scheduleMonthly() {
        if (!enabled) return;
        YearMonth period = YearMonth.from(LocalDate.now(clock.withZone(ZoneId.of(zone)))).minusMonths(1);
        safely(() -> runMonthly(period, "scheduler", "scheduled monthly statement close"));
    }

    public FinancialOperationResult runDaily(LocalDate businessDate, String actor, String reason) {
        validate(actor, reason);
        UUID claimId = UUID.randomUUID();
        if (!claim(DAILY, businessDate, claimId, actor, reason)) {
            return existing(DAILY, businessDate);
        }
        try {
            var result = reconciliation.runDaily(businessDate, actor);
            String evidence = "runId=" + result.runId() + ";exceptions=" + result.totalExceptions()
                    + ";critical=" + result.criticalExceptions() + ";ledgerMutation=false";
            complete(DAILY, businessDate, claimId, evidence);
            return new FinancialOperationResult(DAILY, businessDate, "COMPLETED", true, 1, evidence);
        } catch (RuntimeException failure) {
            fail(DAILY, businessDate, claimId, failure);
            throw failure;
        }
    }

    public FinancialOperationResult runMonthly(YearMonth period, String actor, String reason) {
        validate(actor, reason);
        LocalDate businessDate = period.atEndOfMonth();
        UUID claimId = UUID.randomUUID();
        if (!claim(MONTHLY, businessDate, claimId, actor, reason)) {
            return existing(MONTHLY, businessDate);
        }
        try {
            var accounts = jdbc.queryForList("""
                    SELECT owner_id, external_account_id
                      FROM ledger_accounts
                     WHERE account_kind='CUSTOMER' AND status='ACTIVE'
                       AND owner_id IS NOT NULL AND external_account_id IS NOT NULL
                     ORDER BY owner_id, external_account_id
                    """);
            int generated = 0;
            for (var account : accounts) {
                statements.generate(String.valueOf(account.get("owner_id")),
                        String.valueOf(account.get("external_account_id")), period);
                generated++;
            }
            String evidence = "period=" + period + ";accounts=" + generated
                    + ";source=immutable-posted-ledger;balanceMutation=false";
            complete(MONTHLY, businessDate, claimId, evidence);
            return new FinancialOperationResult(MONTHLY, businessDate, "COMPLETED", true, generated, evidence);
        } catch (RuntimeException failure) {
            fail(MONTHLY, businessDate, claimId, failure);
            throw failure;
        }
    }

    private boolean claim(String type, LocalDate date, UUID claimId, String actor, String reason) {
        Instant now = clock.instant();
        int changed = jdbc.update("""
                INSERT INTO financial_operation_runs
                    (operation_type,business_date,status,attempt_count,claim_id,claimed_by,reason,zone_id,started_at,claim_until)
                VALUES (?,?, 'RUNNING',1,?,?,?,?,?,?)
                ON CONFLICT (operation_type,business_date) DO UPDATE SET
                    status='RUNNING', attempt_count=financial_operation_runs.attempt_count+1,
                    claim_id=EXCLUDED.claim_id, claimed_by=EXCLUDED.claimed_by, reason=EXCLUDED.reason,
                    zone_id=EXCLUDED.zone_id, started_at=EXCLUDED.started_at,
                    claim_until=EXCLUDED.claim_until, completed_at=NULL, error_code=NULL
                WHERE financial_operation_runs.status='FAILED'
                   OR (financial_operation_runs.status='RUNNING' AND financial_operation_runs.claim_until < EXCLUDED.started_at)
                """, type, Date.valueOf(date), claimId, actor, reason, zone,
                Timestamp.from(now), Timestamp.from(now.plusSeconds(Math.max(60, claimLeaseSeconds))));
        return changed == 1;
    }

    private void complete(String type, LocalDate date, UUID claimId, String evidence) {
        int changed = jdbc.update("""
                UPDATE financial_operation_runs SET status='COMPLETED',completed_at=?,claim_until=?,evidence=?,error_code=NULL
                 WHERE operation_type=? AND business_date=? AND claim_id=? AND status='RUNNING'
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), evidence,
                type, Date.valueOf(date), claimId);
        if (changed != 1) throw new IllegalStateException("Financial operation claim was lost before completion");
        completed.increment();
        lastSuccessEpoch.set(clock.instant().getEpochSecond());
    }

    private void fail(String type, LocalDate date, UUID claimId, RuntimeException failureCause) {
        jdbc.update("""
                UPDATE financial_operation_runs SET status='FAILED',completed_at=?,claim_until=?,error_code=?
                 WHERE operation_type=? AND business_date=? AND claim_id=? AND status='RUNNING'
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()),
                failureCause.getClass().getSimpleName(), type, Date.valueOf(date), claimId);
        failed.increment();
    }

    private FinancialOperationResult existing(String type, LocalDate date) {
        return jdbc.queryForObject("""
                SELECT status,evidence FROM financial_operation_runs WHERE operation_type=? AND business_date=?
                """, (rs, row) -> new FinancialOperationResult(type, date, rs.getString("status"), false, 0,
                        rs.getString("evidence")), type, Date.valueOf(date));
    }

    private void safely(Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException failureCause) {
            log.error("Scheduled financial operation failed closed: {}", failureCause.getClass().getSimpleName());
        }
    }

    private void validate(String actor, String reason) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("Operator identity is required");
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A bounded operation reason is required");
        }
        ZoneId.of(zone);
    }
}
