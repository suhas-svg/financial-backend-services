package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerPostingService {

    private final LedgerAccountRepository accountRepository;
    private final JournalTransactionRepository journalRepository;
    private final JournalPostingRepository postingRepository;
    private final JournalStateEventRepository stateRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final LedgerIdempotencyLock idempotencyLock;
    private final LedgerProjectionOutboxService projectionOutboxService;

    public LedgerPostingService(
            LedgerAccountRepository accountRepository,
            JournalTransactionRepository journalRepository,
            JournalPostingRepository postingRepository,
            JournalStateEventRepository stateRepository,
            LedgerBalanceProjectionRepository projectionRepository,
            LedgerIdempotencyLock idempotencyLock,
            LedgerProjectionOutboxService projectionOutboxService) {
        this.accountRepository = accountRepository;
        this.journalRepository = journalRepository;
        this.postingRepository = postingRepository;
        this.stateRepository = stateRepository;
        this.projectionRepository = projectionRepository;
        this.idempotencyLock = idempotencyLock;
        this.projectionOutboxService = projectionOutboxService;
    }

    @Transactional
    public JournalResult createPending(JournalCommand command) {
        idempotencyLock.acquire(command.idempotencyScope(), command.idempotencyKey());
        Optional<JournalTransaction> replay = journalRepository
                .findByIdempotencyScopeAndIdempotencyKey(command.idempotencyScope(), command.idempotencyKey());
        if (replay.isPresent()) {
            return replay(command, replay.get());
        }

        JournalDraft draft = command.draft();
        List<UUID> accountIds = draft.postings().stream()
                .map(PostingDraft::ledgerAccountId)
                .distinct()
                .sorted()
                .toList();
        Map<UUID, LedgerAccount> accounts = toAccountMap(accountRepository.findAllById(accountIds));
        requirePostableAccounts(command.currency(), accountIds, accounts);
        Map<UUID, LedgerBalanceProjection> projections = toProjectionMap(
                projectionRepository.lockAllOrdered(accountIds));
        requireAllProjections(accountIds, projections);

        UUID journalId = UUID.randomUUID();
        JournalTransaction journal = journalRepository.save(JournalTransaction.builder()
                .journalId(journalId)
                .journalReference("JRN-" + journalId)
                .journalType(command.journalType())
                .currency(command.currency())
                .effectiveDate(command.effectiveDate())
                .description(command.description())
                .correlationId(command.correlationId())
                .createdBy(command.createdBy())
                .createdAt(LocalDateTime.now())
                .idempotencyScope(command.idempotencyScope())
                .idempotencyKey(command.idempotencyKey())
                .requestFingerprint(command.requestFingerprint())
                .reversalOfJournalId(command.reversalOfJournalId())
                .build());

        List<JournalPosting> postings = new ArrayList<>();
        int sequence = 1;
        for (PostingDraft posting : draft.postings()) {
            postings.add(JournalPosting.builder()
                    .postingId(UUID.randomUUID())
                    .journalId(journalId)
                    .ledgerAccountId(posting.ledgerAccountId())
                    .postingSequence(sequence++)
                    .direction(posting.direction())
                    .amount(posting.amount())
                    .currency(posting.currency())
                    .memo(posting.memo())
                    .build());
            LedgerBalanceProjection projection = projections.get(posting.ledgerAccountId());
            long projectionEventSequence = projection.getLastEventSequence() + 1L;
            if (posting.direction() == PostingDirection.DEBIT) {
                LedgerAccount account = accounts.get(posting.ledgerAccountId());
                if (account.getAccountKind() == LedgerAccountKind.CUSTOMER) {
                    projection.reserveDebit(posting.amount(), projectionEventSequence);
                } else {
                    projection.reserveDebitAllowNegative(posting.amount(), projectionEventSequence);
                }
            } else {
                projection.reserveCredit(posting.amount(), projectionEventSequence);
            }
        }
        postingRepository.saveAll(postings);
        projectionRepository.saveAll(projections.values());
        JournalStateEvent stateEvent = state(journalId, 1, JournalState.PENDING, command.createdBy(), null);
        stateRepository.save(stateEvent);
        enqueueCustomerProjectionChanges(accounts, projections, stateEvent.getEventId());
        return new JournalResult(journal.getJournalId(), JournalState.PENDING, false);
    }

    @Transactional
    public JournalResult post(UUID journalId, String actor) {
        return complete(journalId, actor, null, JournalState.POSTED);
    }

    @Transactional
    public JournalResult fail(UUID journalId, String actor, String reason) {
        return complete(journalId, actor, reason, JournalState.FAILED);
    }

    @Transactional
    public JournalResult reverse(
            UUID originalJournalId, String actor, String reason, String idempotencyKey) {
        JournalTransaction original = journalRepository.findById(originalJournalId)
                .orElseThrow(() -> new IllegalArgumentException("Journal not found: " + originalJournalId));
        JournalStateEvent originalState = latestState(originalJournalId);
        if (originalState.getState() == JournalState.REVERSED) {
            JournalTransaction existingReversal = journalRepository.findByReversalOfJournalId(originalJournalId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Reversed journal has no compensating journal: " + originalJournalId));
            JournalState state = latestState(existingReversal.getJournalId()).getState();
            return new JournalResult(existingReversal.getJournalId(), state, true);
        }
        if (originalState.getState() != JournalState.POSTED) {
            throw new IllegalArgumentException("Only posted journals can be reversed");
        }

        List<PostingDraft> compensatingPostings = postingRepository
                .findByJournalIdOrderByPostingSequence(originalJournalId)
                .stream()
                .map(posting -> new PostingDraft(
                        posting.getLedgerAccountId(),
                        posting.getDirection() == PostingDirection.DEBIT
                                ? PostingDirection.CREDIT : PostingDirection.DEBIT,
                        posting.getAmount(),
                        posting.getCurrency(),
                        "Reversal of " + original.getJournalReference()))
                .toList();

        JournalCommand command = new JournalCommand(
                JournalType.REVERSAL,
                original.getCurrency(),
                java.time.LocalDate.now(),
                reason,
                original.getCorrelationId(),
                actor,
                actor + ":REVERSAL:" + originalJournalId,
                idempotencyKey,
                JournalRequestFingerprint.forReversal(originalJournalId, reason),
                compensatingPostings,
                originalJournalId);
        JournalResult pending = createPending(command);
        JournalResult posted = post(pending.journalId(), actor);
        stateRepository.save(state(
                originalJournalId,
                originalState.getEventSequence() + 1,
                JournalState.REVERSED,
                actor,
                reason));
        return posted;
    }

    private JournalResult complete(UUID journalId, String actor, String reason, JournalState targetState) {
        JournalTransaction journal = journalRepository.findById(journalId)
                .orElseThrow(() -> new IllegalArgumentException("Journal not found: " + journalId));
        JournalStateEvent latest = latestState(journalId);
        if (latest.getState() == targetState) {
            return new JournalResult(journalId, targetState, true);
        }
        if (latest.getState() != JournalState.PENDING) {
            throw new IllegalArgumentException("Journal is not pending: " + journalId);
        }

        List<JournalPosting> postings = postingRepository.findByJournalIdOrderByPostingSequence(journalId);
        List<UUID> accountIds = postings.stream()
                .map(JournalPosting::getLedgerAccountId)
                .distinct()
                .sorted()
                .toList();
        Map<UUID, LedgerAccount> accounts = toAccountMap(accountRepository.findAllById(accountIds));
        requirePostableAccounts(journal.getCurrency(), accountIds, accounts);
        Map<UUID, LedgerBalanceProjection> projections = toProjectionMap(
                projectionRepository.lockAllOrdered(accountIds));
        requireAllProjections(accountIds, projections);
        for (JournalPosting posting : postings) {
            LedgerBalanceProjection projection = projections.get(posting.getLedgerAccountId());
            long projectionEventSequence = projection.getLastEventSequence() + 1L;
            if (targetState == JournalState.POSTED) {
                if (posting.getDirection() == PostingDirection.DEBIT) {
                    projection.postPendingDebit(posting.getAmount(), projectionEventSequence);
                } else {
                    projection.postPendingCredit(posting.getAmount(), projectionEventSequence);
                }
            } else if (posting.getDirection() == PostingDirection.DEBIT) {
                projection.releasePendingDebit(posting.getAmount(), projectionEventSequence);
            } else {
                projection.releasePendingCredit(posting.getAmount(), projectionEventSequence);
            }
        }
        projectionRepository.saveAll(projections.values());
        JournalStateEvent stateEvent = state(
                journalId, latest.getEventSequence() + 1, targetState, actor, reason);
        stateRepository.save(stateEvent);
        enqueueCustomerProjectionChanges(accounts, projections, stateEvent.getEventId());
        return new JournalResult(journalId, targetState, false);
    }

    private JournalResult replay(JournalCommand command, JournalTransaction existing) {
        if (!existing.getRequestFingerprint().equals(command.requestFingerprint())) {
            throw new IllegalArgumentException("Idempotency key payload conflict");
        }
        JournalStateEvent latest = latestState(existing.getJournalId());
        return new JournalResult(existing.getJournalId(), latest.getState(), true);
    }

    private JournalStateEvent latestState(UUID journalId) {
        return stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(journalId)
                .orElseThrow(() -> new IllegalStateException("Journal has no lifecycle state: " + journalId));
    }

    private Map<UUID, LedgerAccount> toAccountMap(Iterable<LedgerAccount> accounts) {
        Map<UUID, LedgerAccount> result = new HashMap<>();
        accounts.forEach(account -> result.put(account.getLedgerAccountId(), account));
        return result;
    }

    private Map<UUID, LedgerBalanceProjection> toProjectionMap(
            Collection<LedgerBalanceProjection> projections) {
        return projections.stream().collect(Collectors.toMap(
                LedgerBalanceProjection::getLedgerAccountId, Function.identity()));
    }

    private void requirePostableAccounts(
            String currency, Collection<UUID> accountIds, Map<UUID, LedgerAccount> accounts) {
        if (accounts.size() != accountIds.size()) {
            throw new IllegalArgumentException("One or more ledger accounts do not exist");
        }
        for (LedgerAccount account : accounts.values()) {
            if (account.getStatus() != LedgerAccountStatus.ACTIVE) {
                throw new IllegalArgumentException("Ledger account is closed: " + account.getLedgerAccountId());
            }
            if (!currency.equals(account.getCurrency())) {
                throw new IllegalArgumentException("Ledger account currency mismatch");
            }
        }
    }

    private void requireAllProjections(
            Collection<UUID> accountIds, Map<UUID, LedgerBalanceProjection> projections) {
        if (projections.size() != accountIds.size()) {
            throw new IllegalArgumentException("One or more ledger projections do not exist");
        }
    }

    private void enqueueCustomerProjectionChanges(
            Map<UUID, LedgerAccount> accounts,
            Map<UUID, LedgerBalanceProjection> projections,
            UUID sourceEventId) {
        for (Map.Entry<UUID, LedgerBalanceProjection> entry : projections.entrySet()) {
            LedgerAccount account = accounts.get(entry.getKey());
            if (account != null && account.getAccountKind() == LedgerAccountKind.CUSTOMER) {
                projectionOutboxService.enqueue(account, entry.getValue(), sourceEventId);
            }
        }
    }

    private JournalStateEvent state(
            UUID journalId, int sequence, JournalState state, String actor, String reason) {
        return JournalStateEvent.builder()
                .eventId(UUID.randomUUID())
                .journalId(journalId)
                .eventSequence(sequence)
                .state(state)
                .actor(actor)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
