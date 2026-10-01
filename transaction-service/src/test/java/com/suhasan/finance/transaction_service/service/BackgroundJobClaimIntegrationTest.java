package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.entity.ScheduledTransfer;
import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import com.suhasan.finance.transaction_service.entity.Transaction;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.outcome.domain.OutcomeScenario;
import com.suhasan.finance.transaction_service.outcome.repository.OutcomeScenarioRepository;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeMonitorHealth;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeProtectionService;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeScenarioMonitor;
import com.suhasan.finance.transaction_service.repository.ScheduledTransferRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Replica-safety of the background jobs against a real PostgreSQL. Mocks cannot show what
 * {@code FOR UPDATE SKIP LOCKED} does, so each test opens a claim in one transaction, keeps that
 * transaction open (as another replica mid-run would), and then claims from a second transaction:
 * the second claim must return promptly and must never receive a row the first one holds.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.redis.repositories.enabled=false",
        "management.health.redis.enabled=false",
        // Keep the real schedulers out of the way; the tests drive the jobs themselves.
        "transactions.recovery.fixed-delay-ms=3600000",
        "transactions.recovery.initial-delay-ms=3600000",
        "outcome-protection.monitor.fixed-delay-ms=3600000",
        "outcome-protection.monitor.initial-delay-ms=3600000"
})
@Execution(ExecutionMode.SAME_THREAD)
@Testcontainers(disabledWithoutDocker = true)
class BackgroundJobClaimIntegrationTest {

    private static final String EXTERNAL_JDBC_URL = System.getenv("LEDGER_TEST_JDBC_URL");
    private static final String EXTERNAL_USERNAME = System.getenv().getOrDefault("LEDGER_TEST_DB_USER", "test");
    private static final String EXTERNAL_PASSWORD = System.getenv().getOrDefault("LEDGER_TEST_DB_PASSWORD", "test");
    private static final long PROMPT_SECONDS = 10;

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("background_job_claims")
            .withUsername(EXTERNAL_USERNAME)
            .withPassword(EXTERNAL_PASSWORD);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> hasExternalDatabase() ? EXTERNAL_JDBC_URL : postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> hasExternalDatabase() ? EXTERNAL_USERNAME : postgres.getUsername());
        registry.add("spring.datasource.password", () -> hasExternalDatabase() ? EXTERNAL_PASSWORD : postgres.getPassword());
    }

    private static boolean hasExternalDatabase() {
        return EXTERNAL_JDBC_URL != null && !EXTERNAL_JDBC_URL.isBlank();
    }

    /** Its startup run would otherwise process the due schedules these tests insert. */
    @MockitoBean private ScheduledTransferScheduler scheduledTransferScheduler;

    @Autowired private ScheduledTransferRepository scheduleRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private OutcomeScenarioRepository scenarioRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OutcomeMonitorHealth monitorHealth;

    private ExecutorService pool;

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE scheduled_transfer_runs, scheduled_transfers, outcome_scenarios, "
                + "transactions CASCADE");
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void shutDown() {
        pool.shutdownNow();
    }

    // ---------------------------------------------------------------- scheduled transfers

    @Test
    void replicasClaimDisjointDueSchedulesWithoutQueueing() throws Exception {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(scheduleRepository.saveAndFlush(dueSchedule(i)).getScheduleId());
        }
        Instant now = Instant.now();

        Held<List<String>> first = holdOpen(() -> scheduleIds(scheduleRepository.claimDueActive(now, 3)));
        List<String> second = claimPromptly(() -> scheduleIds(scheduleRepository.claimDueActive(now, 3)));
        first.release();

        assertThat(first.result()).hasSize(3);
        assertThat(second).as("the second replica gets the remaining rows, not the first replica's").hasSize(3);
        assertThat(second).doesNotContainAnyElementsOf(first.result());
        assertThat(union(first.result(), second)).containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    void aDueScheduleIsClaimableAgainOnceTheOtherReplicaCommits() throws Exception {
        String only = scheduleRepository.saveAndFlush(dueSchedule(0)).getScheduleId();
        Instant now = Instant.now();

        Held<List<String>> first = holdOpen(() -> scheduleIds(scheduleRepository.claimDueActive(now, 5)));
        assertThat(claimPromptly(() -> scheduleIds(scheduleRepository.claimDueActive(now, 5)))).isEmpty();
        first.release();

        assertThat(first.result()).containsExactly(only);
        assertThat(claimPromptly(() -> scheduleIds(scheduleRepository.claimDueActive(now, 5)))).containsExactly(only);
    }

    @Test
    void schedulesThatAreNotDueOrNotActiveAreNeverClaimed() {
        scheduleRepository.saveAndFlush(dueSchedule(0));
        ScheduledTransfer future = dueSchedule(1);
        future.setNextRunAt(Instant.now().plusSeconds(3600));
        scheduleRepository.saveAndFlush(future);
        ScheduledTransfer paused = dueSchedule(2);
        paused.setStatus(com.suhasan.finance.transaction_service.entity.ScheduledTransferStatus.PAUSED);
        scheduleRepository.saveAndFlush(paused);

        List<String> claimed = transactionTemplate.execute(status ->
                scheduleIds(scheduleRepository.claimDueActive(Instant.now(), 10)));

        assertThat(claimed).hasSize(1);
    }

    // ---------------------------------------------------------------- stale transaction recovery

    @Test
    void replicasClaimDisjointStaleTransactionsAndIgnoreFreshOnes() throws Exception {
        List<String> stale = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stale.add(transactionRepository.saveAndFlush(processingTransaction(30 + i)).getTransactionId());
        }
        transactionRepository.saveAndFlush(processingTransaction(1));                       // too fresh
        Transaction done = processingTransaction(60);
        done.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.saveAndFlush(done);                                           // not PROCESSING
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);

        Held<List<String>> first = holdOpen(() -> transactionIds(transactionRepository.claimStaleProcessing(cutoff, 3)));
        List<String> second = claimPromptly(() -> transactionIds(transactionRepository.claimStaleProcessing(cutoff, 10)));
        first.release();

        assertThat(first.result()).hasSize(3);
        assertThat(second).hasSize(2).doesNotContainAnyElementsOf(first.result());
        assertThat(union(first.result(), second)).containsExactlyInAnyOrderElementsOf(stale);
    }

    @Test
    void recoveryLeavesATransactionAnotherWorkerIsCompletingAlone() throws Exception {
        Transaction beingCompleted = transactionRepository.saveAndFlush(processingTransaction(30));
        Transaction abandoned = transactionRepository.saveAndFlush(processingTransaction(31));

        // Another worker (a request finishing the transaction, or another replica's recovery) holds the row.
        Held<String> holder = holdOpen(() -> transactionRepository
                .findByIdWithLock(beingCompleted.getTransactionId()).orElseThrow().getTransactionId());
        Future<?> recovery = pool.submit(() -> transactionService.processPendingTransactions());
        recovery.get(PROMPT_SECONDS, TimeUnit.SECONDS);   // must not wait for the held row
        holder.release();

        assertThat(statusOf(abandoned.getTransactionId()))
                .as("an abandoned transaction with no posted journal is still failed for manual action")
                .isEqualTo("FAILED");
        assertThat(statusOf(beingCompleted.getTransactionId()))
                .as("recovery must not overwrite a transaction another worker holds")
                .isEqualTo("PROCESSING");
    }

    // ---------------------------------------------------------------- outcome monitor

    @Test
    void replicasClaimDifferentScenariosAndIgnoreArchivedOnes() throws Exception {
        for (int i = 0; i < 3; i++) {
            scenarioRepository.saveAndFlush(scenario("active-" + i, "ACTIVE", 10 - i));
        }
        scenarioRepository.saveAndFlush(scenario("archived", "ARCHIVED", 99));
        List<String> nothingVisited = List.of("-");

        LocalDateTime dueBefore = everythingIsDue();

        Held<String> first = holdOpen(() -> scenarioRepository.claimNextActiveForMonitoring(dueBefore, nothingVisited)
                .orElseThrow().getScenarioId());
        String second = claimPromptly(() -> scenarioRepository.claimNextActiveForMonitoring(dueBefore, nothingVisited)
                .orElseThrow().getScenarioId());
        first.release();

        assertThat(second).isNotEqualTo(first.result()).isNotEqualTo("archived");
        assertThat(first.result()).isNotEqualTo("archived");
    }

    @Test
    void theOldestCheckedScenarioIsClaimedFirstAndVisitedOnesAreSkipped() {
        scenarioRepository.saveAndFlush(scenario("newer", "ACTIVE", 5));
        scenarioRepository.saveAndFlush(scenario("oldest", "ACTIVE", 50));
        scenarioRepository.saveAndFlush(scenario("middle", "ACTIVE", 20));

        LocalDateTime dueBefore = everythingIsDue();

        String first = transactionTemplate.execute(status ->
                scenarioRepository.claimNextActiveForMonitoring(dueBefore, List.of("-")).orElseThrow().getScenarioId());
        String afterVisitingFirst = transactionTemplate.execute(status ->
                scenarioRepository.claimNextActiveForMonitoring(dueBefore, List.of("-", "oldest"))
                        .orElseThrow().getScenarioId());
        boolean nothingLeft = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                scenarioRepository.claimNextActiveForMonitoring(dueBefore, List.of("-", "oldest", "middle", "newer"))
                        .isEmpty()));

        assertThat(first).isEqualTo("oldest");
        assertThat(afterVisitingFirst).isEqualTo("middle");
        assertThat(nothingLeft).isTrue();
    }

    @Test
    void aScenarioCheckedSinceTheRunBeganIsNotClaimedAgain() {
        scenarioRepository.saveAndFlush(scenario("stale", "ACTIVE", 10));      // last checked 10 minutes ago
        scenarioRepository.saveAndFlush(scenario("just-checked", "ACTIVE", 0)); // another replica refreshed it now
        LocalDateTime runBegan = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10);

        // Also proves the UTC wall-clock cutoff lines up with how last_checked_at is stored, whatever the JVM zone.
        List<String> claimable = new ArrayList<>();
        List<String> visited = new ArrayList<>(List.of("-"));
        for (int i = 0; i < 3; i++) {
            String next = transactionTemplate.execute(status -> scenarioRepository
                    .claimNextActiveForMonitoring(runBegan, visited).map(OutcomeScenario::getScenarioId).orElse(null));
            if (next == null) {
                break;
            }
            claimable.add(next);
            visited.add(next);
        }

        assertThat(claimable).containsExactly("stale");
    }

    @Test
    void twoMonitorsRunningTogetherEvaluateEveryScenarioExactlyOnce() throws Exception {
        int scenarios = 8;
        for (int i = 0; i < scenarios; i++) {
            scenarioRepository.saveAndFlush(scenario("s-" + i, "ACTIVE", 100 - i));
        }
        Map<String, AtomicInteger> evaluations = new ConcurrentHashMap<>();
        OutcomeProtectionService protection = Mockito.mock(OutcomeProtectionService.class);
        Mockito.when(protection.refreshClaimed(Mockito.any())).thenAnswer(invocation -> {
            OutcomeScenario claimed = invocation.getArgument(0);
            evaluations.computeIfAbsent(claimed.getScenarioId(), id -> new AtomicInteger()).incrementAndGet();
            Thread.sleep(150);   // work done while the row lock is held, as in production
            claimed.setLastCheckedAt(Instant.now());   // what the real refresh records
            return null;
        });
        OutcomeScenarioMonitor replicaA = new OutcomeScenarioMonitor(scenarioRepository, protection, transactionTemplate, monitorHealth);
        OutcomeScenarioMonitor replicaB = new OutcomeScenarioMonitor(scenarioRepository, protection, transactionTemplate, monitorHealth);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> runA = pool.submit(() -> { awaitQuietly(start); replicaA.monitorActiveScenarios(); });
        Future<?> runB = pool.submit(() -> { awaitQuietly(start); replicaB.monitorActiveScenarios(); });
        start.countDown();
        runA.get(60, TimeUnit.SECONDS);
        runB.get(60, TimeUnit.SECONDS);

        assertThat(evaluations).hasSize(scenarios);
        assertThat(evaluations.values()).allSatisfy(count -> assertThat(count.get())
                .as("a scenario evaluated by both replicas would collide on its @Version").isEqualTo(1));
    }

    @Test
    void scenariosThatKeepFailingBackOffSoAHealthyOneIsStillReached() {
        // One more broken scenario than a monitor run handles (100), all checked longer ago than the healthy one,
        // so without backoff they would fill every run and the healthy scenario would never be checked.
        int brokenCount = 101;
        for (int i = 0; i < brokenCount; i++) {
            scenarioRepository.saveAndFlush(scenario("broken-%03d".formatted(i), "ACTIVE", 120));
        }
        scenarioRepository.saveAndFlush(scenario("healthy", "ACTIVE", 60));
        AtomicInteger healthyChecks = new AtomicInteger();
        OutcomeProtectionService protection = Mockito.mock(OutcomeProtectionService.class);
        Mockito.when(protection.refreshClaimed(Mockito.any())).thenAnswer(invocation -> {
            OutcomeScenario claimed = invocation.getArgument(0);
            if (claimed.getScenarioId().startsWith("broken")) {
                throw new IllegalStateException("Account service unavailable for internal account lookup");
            }
            healthyChecks.incrementAndGet();
            claimed.setLastCheckedAt(Instant.now());
            return null;
        });
        OutcomeScenarioMonitor monitor = new OutcomeScenarioMonitor(
                scenarioRepository, protection, transactionTemplate, monitorHealth);

        monitor.monitorActiveScenarios();
        assertThat(healthyChecks.get()).as("the first run is filled by the 100 oldest, broken, scenarios").isZero();

        monitor.monitorActiveScenarios();
        assertThat(healthyChecks.get()).as("backed-off scenarios no longer crowd out the healthy one").isEqualTo(1);

        Integer backedOff = jdbc.queryForObject("""
                select count(*) from outcome_scenarios
                where scenario_id like 'broken-%'
                  and monitor_failure_count = 1
                  and monitor_next_attempt_at > now() at time zone 'utc'
                  and monitor_last_error like 'Account service unavailable%'
                """, Integer.class);
        assertThat(backedOff).as("every broken scenario was tried once and is waiting out its backoff")
                .isEqualTo(brokenCount);
    }

    // ---------------------------------------------------------------- helpers

    /** A claim whose transaction stays open (holding its row locks) until {@link #release()}. */
    private final class Held<T> {
        private final Future<T> future;
        private final CountDownLatch release;

        private Held(Future<T> future, CountDownLatch release) {
            this.future = future;
            this.release = release;
        }

        void release() {
            release.countDown();
        }

        T result() throws Exception {
            return future.get(PROMPT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private <T> Held<T> holdOpen(Supplier<T> claim) throws Exception {
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<T> future = pool.submit(() -> transactionTemplate.execute(status -> {
            T result = claim.get();
            claimed.countDown();
            awaitQuietly(release);
            return result;
        }));
        if (!claimed.await(PROMPT_SECONDS, TimeUnit.SECONDS)) {
            future.get(1, TimeUnit.SECONDS);   // surfaces the first claim's own failure, if it had one
            fail("the first claim never completed");
        }
        return new Held<>(future, release);
    }

    /** Runs a claim in its own transaction while another is open; blocking here is the bug being tested. */
    private <T> T claimPromptly(Supplier<T> claim) throws Exception {
        Future<T> future = pool.submit(() -> transactionTemplate.execute(status -> claim.get()));
        try {
            return future.get(PROMPT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException blocked) {
            future.cancel(true);
            throw new AssertionError("the second claim blocked behind the first replica's row locks", blocked);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** A cutoff later than any test scenario's last check, so only the lock and ordering decide the claim. */
    private static LocalDateTime everythingIsDue() {
        return LocalDateTime.now(ZoneOffset.UTC).plusDays(1);
    }

    private static List<String> union(Collection<String> a, Collection<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static List<String> scheduleIds(List<ScheduledTransfer> rows) {
        return rows.stream().map(ScheduledTransfer::getScheduleId).toList();
    }

    private static List<String> transactionIds(List<Transaction> rows) {
        return rows.stream().map(Transaction::getTransactionId).toList();
    }

    private String statusOf(String transactionId) {
        return jdbc.queryForObject("select status from transactions where transaction_id = ?",
                String.class, transactionId);
    }

    private static ScheduledTransfer dueSchedule(int index) {
        return ScheduledTransfer.builder()
                .userId("customer").fromAccountId("1").toAccountId("2")
                .amount(new BigDecimal("1.00")).currency("USD")
                .scheduleType(ScheduledTransferType.ONE_TIME)
                .nextRunAt(Instant.now().minusSeconds(120L - index))
                .build();
    }

    private static Transaction processingTransaction(int minutesOld) {
        return Transaction.builder()
                .fromAccountId("EXTERNAL").toAccountId("acc-" + minutesOld)
                .amount(new BigDecimal("5.00")).type(TransactionType.DEPOSIT)
                .status(TransactionStatus.PROCESSING).createdBy("customer")
                .createdAt(LocalDateTime.now().minusMinutes(minutesOld))
                .build();
    }

    private static OutcomeScenario scenario(String id, String status, int minutesSinceChecked) {
        return OutcomeScenario.builder()
                .scenarioId(id).userId("customer").name(id).status(status).currentVersion(1)
                .currency("USD").timeZone("UTC")
                .createIdempotencyKey("key-" + id).createRequestFingerprint("fingerprint-" + id)
                .lastCheckedAt(Instant.now().minusSeconds(60L * minutesSinceChecked))
                .build();
    }
}
