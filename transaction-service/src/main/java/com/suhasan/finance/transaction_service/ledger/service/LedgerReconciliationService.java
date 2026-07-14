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
    private final LedgerAccountRepository accountRepository;
    private final ReconciliationCheckResultRepository checkResultRepository;

    public LedgerReconciliationService(
            ReconciliationRunRepository runRepository,
            ReconciliationExceptionRepository exceptionRepository,
            JournalTransactionRepository journalRepository,
            JournalPostingRepository postingRepository,
            JournalStateEventRepository stateRepository,
            LedgerBalanceProjectionRepository projectionRepository,
            ReconciliationExceptionNoteRepository noteRepository,
            LedgerAccountRepository accountRepository,
            ReconciliationCheckResultRepository checkResultRepository) {
        this.runRepository = runRepository;
        this.exceptionRepository = exceptionRepository;
        this.journalRepository = journalRepository;
        this.postingRepository = postingRepository;
        this.stateRepository = stateRepository;
        this.projectionRepository = projectionRepository;
        this.noteRepository = noteRepository;
        this.accountRepository = accountRepository;
        this.checkResultRepository = checkResultRepository;
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
        int checkedJournals = 0;
        int journalExceptions = 0;

        for (JournalTransaction journal : journalRepository.findAllByEffectiveDateLessThanEqual(businessDate)) {
            Optional<JournalStateEvent> latestState =
                    stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(journal.getJournalId());
            if (latestState.isEmpty()
                    || (latestState.get().getState() != JournalState.POSTED
                    && latestState.get().getState() != JournalState.REVERSED)) {
                continue;
            }
            checkedJournals++;
            List<JournalPosting> postings = postingRepository
                    .findByJournalIdOrderByPostingSequence(journal.getJournalId());
            journalExceptions += reconcileJournalBalance(run.getRunId(), journal, postings, counters);
            accumulatePostedBalances(postings, recomputedPostedBalances);
        }

        checkResultRepository.save(ReconciliationCheckResult.completed(
                run.getRunId(),
                ReconciliationCheckCode.JOURNAL_BALANCE_BY_CURRENCY,
                ReconciliationSeverity.CRITICAL,
                checkedJournals,
                journalExceptions,
                "Verified debit and credit equality for immutable posted journal history"));

        List<LedgerBalanceProjection> projections = Optional.ofNullable(projectionRepository.findAll()).orElse(List.of());
        Map<UUID, LedgerAccount> accounts = accountRepository.findAllById(
                        projections.stream().map(LedgerBalanceProjection::getLedgerAccountId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(LedgerAccount::getLedgerAccountId, account -> account));
        Set<String> observedProjectionFingerprints = new HashSet<>();
        int projectionExceptions = 0;
        for (LedgerBalanceProjection projection : projections) {
            BigDecimal journalMovement = recomputedPostedBalances.getOrDefault(
                    projection.getLedgerAccountId(), BigDecimal.ZERO);
            BigDecimal recomputed = projection.getOpeningBalance().add(journalMovement);
            if (projection.getPostedBalance().compareTo(recomputed) != 0) {
                String fingerprint = "projection:" + projection.getLedgerAccountId() + ":posted-balance";
                observedProjectionFingerprints.add(fingerprint);
                LedgerAccount account = accounts.get(projection.getLedgerAccountId());
                recordException(
                        run.getRunId(),
                        ReconciliationException.projectionDrift(
                                projection.getLedgerAccountId(),
                                account == null ? null : account.getCurrency(),
                                recomputed,
                                projection.getPostedBalance()),
                        counters);
                projectionExceptions++;
            }
        }
        resolveClearedProjectionExceptions(observedProjectionFingerprints);
        checkResultRepository.save(ReconciliationCheckResult.completed(
                run.getRunId(),
                ReconciliationCheckCode.PROJECTION_RECOMPUTATION,
                ReconciliationSeverity.CRITICAL,
                projections.size(),
                projectionExceptions,
                "Compared projections with opening balance plus immutable journal movement"));

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

    private int reconcileJournalBalance(
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
        int exceptions = 0;
        for (String currency : currencies) {
            BigDecimal debit = debits.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal credit = credits.getOrDefault(currency, BigDecimal.ZERO);
            if (debit.compareTo(credit) != 0) {
                recordException(
                        runId,
                        ReconciliationException.journalImbalance(
                                journal.getJournalId(),
                                currency,
                                debit,
                                credit),
                        counters);
                exceptions++;
            }
        }
        return exceptions;
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
            existing.get().refreshEvidenceFrom(exception);
            exceptionRepository.save(existing.get());
            exceptionRepository.linkToRun(runId, existing.get().getExceptionId(), false);
        } else {
            exceptionRepository.save(exception);
            exceptionRepository.linkToRun(runId, exception.getExceptionId(), true);
        }
    }

    private void resolveClearedProjectionExceptions(Set<String> observedFingerprints) {
        for (ReconciliationException exception : exceptionRepository.findActiveByCheckCode(
                ReconciliationCheckCode.PROJECTION_RECOMPUTATION)) {
            if (!observedFingerprints.contains(exception.getFingerprint())) {
                exception.updateStatus(
                        ReconciliationExceptionStatus.RESOLVED,
                        "Automatically resolved after corrected reconciliation confirmed no projection drift",
                        "system",
                        exception.getVersion());
                exceptionRepository.save(exception);
            }
        }
    }

    private static final class ReconciliationCounters {
        private int totalExceptions;
        private int criticalExceptions;
    }
}
