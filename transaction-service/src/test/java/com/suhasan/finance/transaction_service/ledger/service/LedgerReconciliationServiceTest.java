package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerReconciliationServiceTest {

    @Mock private ReconciliationRunRepository runRepository;
    @Mock private ReconciliationExceptionRepository exceptionRepository;
    @Mock private JournalTransactionRepository journalRepository;
    @Mock private JournalPostingRepository postingRepository;
    @Mock private JournalStateEventRepository stateRepository;
    @Mock private LedgerBalanceProjectionRepository projectionRepository;
    @Mock private ReconciliationExceptionNoteRepository noteRepository;
    @Mock private LedgerAccountRepository accountRepository;
    @Mock private ReconciliationCheckResultRepository checkResultRepository;

    private LedgerReconciliationService service;
    private LocalDate businessDate;

    @BeforeEach
    void setUp() {
        service = new LedgerReconciliationService(
                runRepository,
                exceptionRepository,
                journalRepository,
                postingRepository,
                stateRepository,
                projectionRepository,
                noteRepository,
                accountRepository,
                checkResultRepository);
        businessDate = LocalDate.of(2026, 6, 24);
    }

    @Test
    void dailyRunUsesAdvisoryLockToPreventConcurrentRunsForSameBusinessDate() {
        when(runRepository.tryAcquireDailyRunLock(businessDate, ReconciliationType.DAILY_LEDGER))
                .thenReturn(false);

        assertThatThrownBy(() -> service.runDaily(businessDate, "ops"))
                .isInstanceOf(ReconciliationAlreadyRunningException.class)
                .hasMessageContaining("already running");

        verify(runRepository).tryAcquireDailyRunLock(businessDate, ReconciliationType.DAILY_LEDGER);
        verifyNoInteractions(journalRepository, postingRepository, projectionRepository, exceptionRepository);
    }

    @Test
    void unbalancedPostedJournalCreatesCriticalExceptionWithStableFingerprintAndDedupesOpenException() {
        UUID journalId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        JournalTransaction journal = journal(journalId, "JRN-1", businessDate);
        ReconciliationException existing = ReconciliationException.open(
                ReconciliationCheckCode.JOURNAL_BALANCE_BY_CURRENCY,
                ReconciliationSeverity.CRITICAL,
                "journal:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:USD:journal-balance",
                journalId,
                null,
                "Existing imbalance");
        when(runRepository.tryAcquireDailyRunLock(businessDate, ReconciliationType.DAILY_LEDGER))
                .thenReturn(true);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalRepository.findAllByEffectiveDateLessThanEqual(businessDate)).thenReturn(List.of(journal));
        when(stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(journalId))
                .thenReturn(Optional.of(state(journalId, JournalState.POSTED)));
        when(postingRepository.findByJournalIdOrderByPostingSequence(journalId)).thenReturn(List.of(
                posting(journalId, PostingDirection.DEBIT, "10.00"),
                posting(journalId, PostingDirection.CREDIT, "9.99")));
        when(exceptionRepository.findOpenByFingerprint(existing.getFingerprint()))
                .thenReturn(Optional.of(existing));

        ReconciliationRunResult result = service.runDaily(businessDate, "ops");

        assertThat(result.status()).isEqualTo(ReconciliationRunStatus.COMPLETED_WITH_EXCEPTIONS);
        assertThat(result.criticalExceptions()).isEqualTo(1);
        verify(exceptionRepository).linkToRun(result.runId(), existing.getExceptionId(), false);
        verify(exceptionRepository).save(existing);
    }

    @Test
    void projectionRecomputationDriftCreatesCriticalExceptionWithoutMutatingProjection() {
        UUID accountId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID journalId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        JournalTransaction journal = journal(journalId, "JRN-2", businessDate);
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(accountId, new BigDecimal("99.00"));
        when(runRepository.tryAcquireDailyRunLock(businessDate, ReconciliationType.DAILY_LEDGER))
                .thenReturn(true);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalRepository.findAllByEffectiveDateLessThanEqual(businessDate)).thenReturn(List.of(journal));
        when(stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(journalId))
                .thenReturn(Optional.of(state(journalId, JournalState.POSTED)));
        when(postingRepository.findByJournalIdOrderByPostingSequence(journalId)).thenReturn(List.of(
                posting(journalId, UUID.randomUUID(), PostingDirection.DEBIT, "100.00"),
                posting(journalId, accountId, PostingDirection.CREDIT, "100.00")));
        when(projectionRepository.findAll()).thenReturn(List.of(projection));
        when(exceptionRepository.findOpenByFingerprint(
                "projection:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb:posted-balance"))
                .thenReturn(Optional.empty());
        when(exceptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReconciliationRunResult result = service.runDaily(businessDate, "ops");

        assertThat(result.criticalExceptions()).isEqualTo(1);
        verify(exceptionRepository).save(argThat(saved ->
                saved instanceof ReconciliationException exception
                        && exception.getCheckCode() == ReconciliationCheckCode.PROJECTION_RECOMPUTATION
                        && exception.getSeverity() == ReconciliationSeverity.CRITICAL
                        && exception.getFingerprint().equals(
                                "projection:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb:posted-balance")));
        verify(projectionRepository, never()).save(any());
        assertThat(projection.getPostedBalance()).isEqualByComparingTo("99.00");
    }

    @Test
    void projectionRecomputationIncludesOpeningBalanceAndImmutableReversedJournalHistory() {
        UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID clearingId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID originalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID reversalId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        JournalTransaction original = journal(originalId, "JRN-ORIGINAL", businessDate);
        JournalTransaction reversal = journal(reversalId, "JRN-REVERSAL", businessDate);
        LedgerBalanceProjection customerProjection = LedgerBalanceProjection.open(accountId, new BigDecimal("100.00"));
        LedgerBalanceProjection clearingProjection = LedgerBalanceProjection.open(clearingId, BigDecimal.ZERO);

        when(runRepository.tryAcquireDailyRunLock(businessDate, ReconciliationType.DAILY_LEDGER)).thenReturn(true);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalRepository.findAllByEffectiveDateLessThanEqual(businessDate)).thenReturn(List.of(original, reversal));
        when(stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(originalId))
                .thenReturn(Optional.of(state(originalId, JournalState.REVERSED)));
        when(stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(reversalId))
                .thenReturn(Optional.of(state(reversalId, JournalState.POSTED)));
        when(postingRepository.findByJournalIdOrderByPostingSequence(originalId)).thenReturn(List.of(
                posting(originalId, clearingId, PostingDirection.DEBIT, "1.25"),
                posting(originalId, accountId, PostingDirection.CREDIT, "1.25")));
        when(postingRepository.findByJournalIdOrderByPostingSequence(reversalId)).thenReturn(List.of(
                posting(reversalId, clearingId, PostingDirection.CREDIT, "1.25"),
                posting(reversalId, accountId, PostingDirection.DEBIT, "1.25")));
        when(projectionRepository.findAll()).thenReturn(List.of(customerProjection, clearingProjection));

        ReconciliationRunResult result = service.runDaily(businessDate, "ops");

        assertThat(result.status()).isEqualTo(ReconciliationRunStatus.COMPLETED);
        assertThat(result.totalExceptions()).isZero();
        verify(exceptionRepository, never()).save(argThat(value -> value.getCheckCode() == ReconciliationCheckCode.PROJECTION_RECOMPUTATION));
        verify(checkResultRepository, times(2)).save(any(ReconciliationCheckResult.class));
    }

    @Test
    void resolvingOrWaivingExceptionRequiresNoteAndDoesNotMutateLedgerHistory() {
        UUID exceptionId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        ReconciliationException exception = ReconciliationException.open(
                ReconciliationCheckCode.PROJECTION_RECOMPUTATION,
                ReconciliationSeverity.CRITICAL,
                "projection:account-1:posted-balance",
                null,
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                "Projection drift");
        when(exceptionRepository.findById(exceptionId)).thenReturn(Optional.of(exception));

        assertThatThrownBy(() -> service.updateExceptionStatus(
                exceptionId, ReconciliationExceptionStatus.RESOLVED, "", "ops", exception.getVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note is required");

        service.updateExceptionStatus(
                exceptionId,
                ReconciliationExceptionStatus.RESOLVED,
                "Corrected through compensating journal JRN-9",
                "ops",
                exception.getVersion());

        assertThat(exception.getStatus()).isEqualTo(ReconciliationExceptionStatus.RESOLVED);
        assertThat(exception.getResolutionNote()).contains("compensating journal");
        verify(exceptionRepository).save(exception);
        verifyNoInteractions(journalRepository, postingRepository, projectionRepository);
    }

    private JournalTransaction journal(UUID journalId, String reference, LocalDate effectiveDate) {
        return JournalTransaction.builder()
                .journalId(journalId)
                .journalReference(reference)
                .journalType(JournalType.TRANSFER)
                .currency("USD")
                .effectiveDate(effectiveDate)
                .description(reference)
                .correlationId(reference)
                .createdBy("user-1")
                .createdAt(LocalDateTime.now())
                .idempotencyScope("scope")
                .idempotencyKey(reference)
                .requestFingerprint(reference)
                .build();
    }

    private JournalPosting posting(UUID journalId, PostingDirection direction, String amount) {
        return posting(journalId, UUID.randomUUID(), direction, amount);
    }

    private JournalPosting posting(UUID journalId, UUID ledgerAccountId, PostingDirection direction, String amount) {
        return JournalPosting.builder()
                .postingId(UUID.randomUUID())
                .journalId(journalId)
                .ledgerAccountId(ledgerAccountId)
                .postingSequence(1)
                .direction(direction)
                .amount(new BigDecimal(amount))
                .currency("USD")
                .build();
    }

    private JournalStateEvent state(UUID journalId, JournalState state) {
        return JournalStateEvent.builder()
                .eventId(UUID.randomUUID())
                .journalId(journalId)
                .eventSequence(1)
                .state(state)
                .actor("worker")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
