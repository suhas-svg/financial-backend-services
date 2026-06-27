package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.redis.repositories.enabled=false",
        "management.health.redis.enabled=false"
})
@Execution(ExecutionMode.SAME_THREAD)
@Testcontainers(disabledWithoutDocker = true)
class LedgerPostingConcurrencyIntegrationTest {

    private static final String EXTERNAL_JDBC_URL = System.getenv("LEDGER_TEST_JDBC_URL");
    private static final String EXTERNAL_USERNAME = System.getenv().getOrDefault("LEDGER_TEST_DB_USER", "test");
    private static final String EXTERNAL_PASSWORD = System.getenv().getOrDefault("LEDGER_TEST_DB_PASSWORD", "test");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ledger_concurrency")
            .withUsername(EXTERNAL_USERNAME)
            .withPassword(EXTERNAL_PASSWORD);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> hasExternalDatabase() ? EXTERNAL_JDBC_URL : postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> hasExternalDatabase() ? EXTERNAL_USERNAME : postgres.getUsername());
        registry.add("spring.datasource.password", () -> hasExternalDatabase() ? EXTERNAL_PASSWORD : postgres.getPassword());
    }

    @AfterAll
    static void stopContainer() {
        // The Testcontainers JUnit extension owns container lifecycle.
    }

    private static boolean hasExternalDatabase() {
        return EXTERNAL_JDBC_URL != null && !EXTERNAL_JDBC_URL.isBlank();
    }

    @Autowired private LedgerPostingService postingService;
    @Autowired private LedgerAccountRepository accountRepository;
    @Autowired private LedgerBalanceProjectionRepository projectionRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void resetLedger() {
        jdbc.execute("TRUNCATE TABLE ledger_projection_outbox, journal_links, journal_state_events, "
                + "journal_postings, ledger_idempotency_claims, journal_transactions, "
                + "ledger_balance_projections, ledger_accounts CASCADE");
    }

    @Test
    void concurrentDuplicateCommandsReturnOneJournal() throws Exception {
        Accounts accounts = seedAccounts(new BigDecimal("100.00"), 1);
        JournalCommand command = transfer(accounts.source(), accounts.destinations().getFirst(),
                new BigDecimal("25.00"), "duplicate-key", "same-fingerprint");

        List<Attempt> attempts = runConcurrently(command, command);

        assertThat(attempts).allMatch(attempt -> attempt.error() == null);
        assertThat(attempts).extracting(attempt -> attempt.result().journalId())
                .containsOnly(attempts.getFirst().result().journalId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_transactions", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_postings", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentDebitsCannotReserveMoreThanAvailable() throws Exception {
        Accounts accounts = seedAccounts(new BigDecimal("100.00"), 2);
        JournalCommand first = transfer(accounts.source(), accounts.destinations().get(0),
                new BigDecimal("80.00"), "debit-one", "fingerprint-one");
        JournalCommand second = transfer(accounts.source(), accounts.destinations().get(1),
                new BigDecimal("80.00"), "debit-two", "fingerprint-two");

        List<Attempt> attempts = runConcurrently(first, second);

        assertThat(attempts.stream().filter(attempt -> attempt.error() == null)).hasSize(1);
        assertThat(attempts.stream().filter(attempt -> attempt.error() != null)).singleElement()
                .satisfies(attempt -> assertThat(attempt.error()).hasMessageContaining("Insufficient available balance"));
        LedgerBalanceProjection source = projectionRepository.findById(accounts.source()).orElseThrow();
        assertThat(source.getAvailableBalance()).isEqualByComparingTo("20.00");
    }

    private List<Attempt> runConcurrently(JournalCommand first, JournalCommand second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Attempt> firstResult = executor.submit(() -> attempt(first, barrier));
            Future<Attempt> secondResult = executor.submit(() -> attempt(second, barrier));
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt attempt(JournalCommand command, CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return new Attempt(postingService.createPending(command), null);
        } catch (Exception exception) {
            return new Attempt(null, exception);
        }
    }

    private Accounts seedAccounts(BigDecimal sourceBalance, int destinationCount) {
        UUID sourceId = UUID.randomUUID();
        LedgerAccount source = customer(sourceId, "source-" + sourceId);
        List<LedgerAccount> destinations = java.util.stream.IntStream.range(0, destinationCount)
                .mapToObj(index -> {
                    UUID id = UUID.randomUUID();
                    return customer(id, "destination-" + id);
                })
                .toList();
        accountRepository.saveAll(java.util.stream.Stream.concat(
                java.util.stream.Stream.of(source), destinations.stream()).toList());
        projectionRepository.save(LedgerBalanceProjection.open(sourceId, sourceBalance));
        destinations.forEach(destination -> projectionRepository.save(
                LedgerBalanceProjection.open(destination.getLedgerAccountId(), BigDecimal.ZERO)));
        return new Accounts(sourceId, destinations.stream().map(LedgerAccount::getLedgerAccountId).toList());
    }

    private LedgerAccount customer(UUID id, String externalId) {
        return LedgerAccount.builder()
                .ledgerAccountId(id)
                .accountKind(LedgerAccountKind.CUSTOMER)
                .currency("USD")
                .externalAccountId(externalId)
                .ownerId("user-1")
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private JournalCommand transfer(
            UUID source, UUID destination, BigDecimal amount, String key, String fingerprint) {
        return new JournalCommand(
                JournalType.TRANSFER, "USD", LocalDate.now(), "concurrency test", UUID.randomUUID().toString(),
                "user-1", "user-1:TRANSFER", key, fingerprint,
                List.of(
                        new PostingDraft(source, PostingDirection.DEBIT, amount, "USD", "source"),
                        new PostingDraft(destination, PostingDirection.CREDIT, amount, "USD", "destination")));
    }

    private record Accounts(UUID source, List<UUID> destinations) {
    }

    private record Attempt(JournalResult result, Exception error) {
    }
}
