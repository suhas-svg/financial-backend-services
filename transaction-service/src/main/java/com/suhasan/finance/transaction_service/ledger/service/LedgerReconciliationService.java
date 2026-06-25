package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class LedgerReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationExceptionRepository exceptionRepository;
    private final JournalTransactionRepository journalRepository;
    private final JournalPostingRepository postingRepository;
    private final JournalStateEventRepository stateRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final ReconciliationExceptionNoteRepository noteRepository;

    public LedgerReconciliationService(
            ReconciliationRunRepository runRepository,
            ReconciliationExceptionRepository exceptionRepository,
            JournalTransactionRepository journalRepository,
            JournalPostingRepository postingRepository,
            JournalStateEventRepository stateRepository,
            LedgerBalanceProjectionRepository projectionRepository,
            ReconciliationExceptionNoteRepository noteRepository) {
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
        this.journalRepository = journalRepository;
        this.postingRepository = postingRepository;
        this.stateRepository = stateRepository;
        this.projectionRepository = projectionRepository;
        this.noteRepository = noteRepository;
    }

    @Transactional
    public ReconciliationRunResult runDaily(LocalDate businessDate, String requestedBy) {
        if (!runRepository.tryAcquireDailyRunLock(businessDate, ReconciliationType.DAILY_LEDGER)) {
            throw new ReconciliationAlreadyRunningException(
                    "Daily ledger reconciliation is already running for " + businessDate);
        }

        ReconciliationRun run = runRepository.save(
                ReconciliationRun.start(businessDate, ReconciliationType.DAILY_LEDGER, requestedBy));
        ReconciliationCounters counters = new ReconciliationCounters();
        Map<UUID, BigDecimal> recomputedPostedBalances = new HashMap<>();

        for (JournalTransaction journal : journalRepository.findAllByEffectiveDateLessThanEqual(businessDate)) {
            Optional<JournalStateEvent> latestState =
                    stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(journal.getJournalId());
            if (latestState.isEmpty() || latestState.get().getState() != JournalState.POSTED) {
                continue;
            }
            List<JournalPosting> postings = postingRepository
                    .findByJournalIdOrderByPostingSequence(journal.getJournalId());
            reconcileJournalBalance(run.getRunId(), journal, postings, counters);
            accumulatePostedBalances(postings, recomputedPostedBalances);
        }

        for (LedgerBalanceProjection projection : Optional.ofNullable(projectionRepository.findAll()).orElse(List.of())) {
            BigDecimal recomputed = recomputedPostedBalances.getOrDefault(
                    projection.getLedgerAccountId(), BigDecimal.ZERO);
            if (projection.getPostedBalance().compareTo(recomputed) != 0) {
                recordException(
                        run.getRunId(),
                        ReconciliationException.open(
                                ReconciliationCheckCode.PROJECTION_RECOMPUTATION,
                                ReconciliationSeverity.CRITICAL,
                                "projection:" + projection.getLedgerAccountId() + ":posted-balance",
                                null,
                                projection.getLedgerAccountId(),
                                "Projection posted balance does not match recomputed journal total"),
                        counters);
            }
        }

        run.complete(counters.totalExceptions, counters.criticalExceptions);
        runRepository.save(run);
        return new ReconciliationRunResult(
                run.getRunId(),
                run.getBusinessDate(),
                run.getStatus(),
                run.getTotalExceptions(),
                run.getCriticalExceptions());
    }

    @Transactional
    public ReconciliationException updateExceptionStatus(
            UUID exceptionId,
            ReconciliationExceptionStatus status,
            String note,
            String actor,
            long expectedVersion) {
        ReconciliationException exception = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation exception not found: " + exceptionId));
        exception.updateStatus(status, note, actor, expectedVersion);
        return exceptionRepository.save(exception);
    }

    @Transactional
    public ReconciliationException assignException(
            UUID exceptionId,
            String assignee,
            String actor,
            long expectedVersion) {
        ReconciliationException exception = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation exception not found: " + exceptionId));
        exception.assignTo(assignee, actor, expectedVersion);
        return exceptionRepository.save(exception);
    }

    @Transactional
    public ReconciliationException addExceptionNote(UUID exceptionId, String note, String actor) {
        ReconciliationException exception = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation exception not found: " + exceptionId));
        noteRepository.save(ReconciliationExceptionNote.create(exceptionId, actor, note));
        return exception;
    }

    private void reconcileJournalBalance(
            UUID runId,
            JournalTransaction journal,
            List<JournalPosting> postings,
            ReconciliationCounters counters) {
        Map<String, BigDecimal> debits = new HashMap<>();
        Map<String, BigDecimal> credits = new HashMap<>();
        for (JournalPosting posting : postings) {
            Map<String, BigDecimal> target = posting.getDirection() == PostingDirection.DEBIT ? debits : credits;
            target.merge(posting.getCurrency(), posting.getAmount(), BigDecimal::add);
        }
        Set<String> currencies = new HashSet<>();
        currencies.addAll(debits.keySet());
        currencies.addAll(credits.keySet());
        for (String currency : currencies) {
            BigDecimal debit = debits.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal credit = credits.getOrDefault(currency, BigDecimal.ZERO);
            if (debit.compareTo(credit) != 0) {
                recordException(
                        runId,
                        ReconciliationException.open(
                                ReconciliationCheckCode.JOURNAL_BALANCE_BY_CURRENCY,
                                ReconciliationSeverity.CRITICAL,
                                "journal:" + journal.getJournalId() + ":" + currency + ":journal-balance",
                                journal.getJournalId(),
                                null,
                                "Journal debit and credit totals differ for " + currency),
                        counters);
            }
        }
    }

    private void accumulatePostedBalances(
            List<JournalPosting> postings,
            Map<UUID, BigDecimal> recomputedPostedBalances) {
        for (JournalPosting posting : postings) {
            BigDecimal signedAmount = posting.getDirection() == PostingDirection.CREDIT
                    ? posting.getAmount()
                    : posting.getAmount().negate();
            recomputedPostedBalances.merge(posting.getLedgerAccountId(), signedAmount, BigDecimal::add);
        }
    }

    private void recordException(
            UUID runId,
            ReconciliationException exception,
            ReconciliationCounters counters) {
        counters.totalExceptions++;
        if (exception.getSeverity() == ReconciliationSeverity.CRITICAL) {
            counters.criticalExceptions++;
        }
        Optional<ReconciliationException> existing =
                exceptionRepository.findOpenByFingerprint(exception.getFingerprint());
        if (existing.isPresent()) {
            exceptionRepository.linkExistingToRun(existing.get(), runId);
        } else {
            exceptionRepository.save(exception);
        }
    }

    private static final class ReconciliationCounters {
        private int totalExceptions;
        private int criticalExceptions;
    }
}
