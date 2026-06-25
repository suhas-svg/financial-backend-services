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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

    @Mock private LedgerAccountRepository accountRepository;
    @Mock private JournalTransactionRepository journalRepository;
    @Mock private JournalPostingRepository postingRepository;
    @Mock private JournalStateEventRepository stateRepository;
    @Mock private LedgerBalanceProjectionRepository projectionRepository;
    @Mock private LedgerIdempotencyLock idempotencyLock;

    private LedgerPostingService service;
    private UUID sourceId;
    private UUID destinationId;
    private LedgerBalanceProjection sourceProjection;
    private LedgerBalanceProjection destinationProjection;

    @BeforeEach
    void setUp() {
        service = new LedgerPostingService(
                accountRepository, journalRepository, postingRepository, stateRepository, projectionRepository,
                idempotencyLock);
        sourceId = UUID.randomUUID();
        destinationId = UUID.randomUUID();
        sourceProjection = LedgerBalanceProjection.open(sourceId, new BigDecimal("100.00"));
        destinationProjection = LedgerBalanceProjection.open(destinationId, BigDecimal.ZERO);
    }

    @Test
    void createPendingReservesDebitAndCreditInDeterministicAccountOrder() {
        stubNewJournalPersistence();

        JournalResult result = service.createPending(transferCommand("idem-1", "fp-1"));

        assertThat(result.state()).isEqualTo(JournalState.PENDING);
        assertThat(result.replay()).isFalse();
        assertThat(sourceProjection.getAvailableBalance()).isEqualByComparingTo("75.00");
        assertThat(destinationProjection.getPendingCredits()).isEqualByComparingTo("25.00");
        verify(idempotencyLock).acquire("user-1:TRANSFER", "idem-1");
        verify(projectionRepository).lockAllOrdered(argThat(ids -> {
            List<UUID> ordered = List.copyOf(ids);
            return ordered.equals(ordered.stream().sorted().toList());
        }));
    }

    @Test
    void exactIdempotentReplayReturnsExistingJournalWithoutMutatingProjections() {
        JournalTransaction existing = journal("idem-1", "fp-1");
        when(journalRepository.findByIdempotencyScopeAndIdempotencyKey("user-1:TRANSFER", "idem-1"))
                .thenReturn(Optional.of(existing));
        when(stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(existing.getJournalId()))
                .thenReturn(Optional.of(state(existing.getJournalId(), 1, JournalState.PENDING)));

        JournalResult result = service.createPending(transferCommand("idem-1", "fp-1"));

        assertThat(result.journalId()).isEqualTo(existing.getJournalId());
        assertThat(result.replay()).isTrue();
        verify(idempotencyLock).acquire("user-1:TRANSFER", "idem-1");
        verifyNoInteractions(projectionRepository);
    }

    @Test
    void idempotencyKeyReuseWithDifferentFingerprintIsRejected() {
        JournalTransaction existing = journal("idem-1", "fp-original");
        when(journalRepository.findByIdempotencyScopeAndIdempotencyKey("user-1:TRANSFER", "idem-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createPending(transferCommand("idem-1", "fp-other")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency key payload conflict");
    }

    @Test
    void postMovesPendingAmountsIntoPostedBalances() {
        UUID journalId = UUID.randomUUID();
        sourceProjection.reserveDebit(new BigDecimal("25.00"), 1L);
        destinationProjection.reserveCredit(new BigDecimal("25.00"), 1L);
        stubExistingPendingJournal(journalId);

        JournalResult result = service.post(journalId, "worker");

        assertThat(result.state()).isEqualTo(JournalState.POSTED);
        assertThat(sourceProjection.getPostedBalance()).isEqualByComparingTo("75.00");
        assertThat(destinationProjection.getPostedBalance()).isEqualByComparingTo("25.00");
        assertThat(sourceProjection.getPendingBalance()).isZero();
        assertThat(destinationProjection.getPendingBalance()).isZero();
    }

    @Test
    void failReleasesPendingAmountsWithoutChangingPostedBalances() {
        UUID journalId = UUID.randomUUID();
        sourceProjection.reserveDebit(new BigDecimal("25.00"), 1L);
        destinationProjection.reserveCredit(new BigDecimal("25.00"), 1L);
        stubExistingPendingJournal(journalId);

        JournalResult result = service.fail(journalId, "worker", "downstream failure");

        assertThat(result.state()).isEqualTo(JournalState.FAILED);
        assertThat(sourceProjection.getPostedBalance()).isEqualByComparingTo("100.00");
        assertThat(destinationProjection.getPostedBalance()).isZero();
        assertThat(sourceProjection.getAvailableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void systemAccountDebitDoesNotEnforceCustomerAvailableBalance() {
        sourceProjection = LedgerBalanceProjection.open(sourceId, BigDecimal.ZERO);
        when(journalRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(accountRepository.findAllById(any())).thenReturn(List.of(
                LedgerAccount.builder()
                        .ledgerAccountId(sourceId)
                        .accountKind(LedgerAccountKind.CLEARING)
                        .currency("USD")
                        .status(LedgerAccountStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .build(),
                customer(destinationId, "account-2")));
        when(projectionRepository.lockAllOrdered(any())).thenReturn(List.of(sourceProjection, destinationProjection));
        when(journalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        JournalResult result = service.createPending(transferCommand("idem-system", "fp-system"));

        assertThat(result.state()).isEqualTo(JournalState.PENDING);
        assertThat(sourceProjection.getAvailableBalance()).isEqualByComparingTo("-25.00");
    }

    @Test
    void reversalPostsSwappedEntriesAndMarksOriginalReversed() {
        UUID originalId = UUID.randomUUID();
        sourceProjection = LedgerBalanceProjection.open(sourceId, new BigDecimal("75.00"));
        destinationProjection = LedgerBalanceProjection.open(destinationId, new BigDecimal("25.00"));
        JournalTransaction original = JournalTransaction.builder()
                .journalId(originalId)
                .journalReference("JRN-" + originalId)
                .journalType(JournalType.TRANSFER)
                .currency("USD")
                .effectiveDate(LocalDate.now())
                .description("original")
                .correlationId("correlation-original")
                .createdBy("user-1")
                .createdAt(LocalDateTime.now())
                .idempotencyScope("user-1:TRANSFER")
                .idempotencyKey("original-key")
                .requestFingerprint("original-fingerprint")
                .build();
        AtomicReference<JournalTransaction> reversal = new AtomicReference<>();
        Map<UUID, JournalStateEvent> states = new HashMap<>();
        states.put(originalId, state(originalId, 2, JournalState.POSTED));
        Map<UUID, List<JournalPosting>> postings = new HashMap<>();
        postings.put(originalId, List.of(
                posting(originalId, sourceId, 1, PostingDirection.DEBIT),
                posting(originalId, destinationId, 2, PostingDirection.CREDIT)));

        when(journalRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            if (id.equals(originalId)) return Optional.of(original);
            return Optional.ofNullable(reversal.get()).filter(journal -> journal.getJournalId().equals(id));
        });
        when(journalRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(journalRepository.save(any())).thenAnswer(invocation -> {
            JournalTransaction saved = invocation.getArgument(0);
            reversal.set(saved);
            return saved;
        });
        when(stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(any())).thenAnswer(invocation ->
                Optional.ofNullable(states.get(invocation.getArgument(0))));
        when(stateRepository.save(any())).thenAnswer(invocation -> {
            JournalStateEvent saved = invocation.getArgument(0);
            states.put(saved.getJournalId(), saved);
            return saved;
        });
        when(postingRepository.findByJournalIdOrderByPostingSequence(any())).thenAnswer(invocation ->
                postings.get(invocation.getArgument(0)));
        when(postingRepository.saveAll(any())).thenAnswer(invocation -> {
            List<JournalPosting> saved = (List<JournalPosting>) invocation.getArgument(0);
            postings.put(saved.getFirst().getJournalId(), saved);
            return saved;
        });
        when(accountRepository.findAllById(any())).thenReturn(List.of(
                customer(sourceId, "account-1"), customer(destinationId, "account-2")));
        when(projectionRepository.lockAllOrdered(any())).thenReturn(List.of(sourceProjection, destinationProjection));

        JournalResult result = service.reverse(originalId, "admin", "customer correction", "reverse-key");

        assertThat(result.state()).isEqualTo(JournalState.POSTED);
        assertThat(sourceProjection.getPostedBalance()).isEqualByComparingTo("100.00");
        assertThat(destinationProjection.getPostedBalance()).isZero();
        assertThat(states.get(originalId).getState()).isEqualTo(JournalState.REVERSED);
        assertThat(reversal.get().getReversalOfJournalId()).isEqualTo(originalId);
    }

    private void stubNewJournalPersistence() {
        when(journalRepository.findByIdempotencyScopeAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(accountRepository.findAllById(any())).thenReturn(List.of(
                customer(sourceId, "account-1"), customer(destinationId, "account-2")));
        when(projectionRepository.lockAllOrdered(any())).thenReturn(List.of(sourceProjection, destinationProjection));
        when(journalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubExistingPendingJournal(UUID journalId) {
        when(journalRepository.findById(journalId)).thenReturn(Optional.of(JournalTransaction.builder()
                .journalId(journalId)
                .journalReference("JRN-" + journalId)
                .journalType(JournalType.TRANSFER)
                .currency("USD")
                .effectiveDate(LocalDate.now())
                .correlationId(journalId.toString())
                .createdBy("user-1")
                .createdAt(LocalDateTime.now())
                .idempotencyScope("user-1:TRANSFER")
                .idempotencyKey("idem-1")
                .requestFingerprint("fp-1")
                .build()));
        when(stateRepository.findFirstByJournalIdOrderByEventSequenceDesc(journalId))
                .thenReturn(Optional.of(state(journalId, 1, JournalState.PENDING)));
        when(postingRepository.findByJournalIdOrderByPostingSequence(journalId)).thenReturn(List.of(
                posting(journalId, sourceId, 1, PostingDirection.DEBIT),
                posting(journalId, destinationId, 2, PostingDirection.CREDIT)));
        when(projectionRepository.lockAllOrdered(any())).thenReturn(List.of(sourceProjection, destinationProjection));
        when(stateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private JournalCommand transferCommand(String key, String fingerprint) {
        return new JournalCommand(
                JournalType.TRANSFER, "USD", LocalDate.now(), "transfer", "correlation-1",
                "user-1", "user-1:TRANSFER", key, fingerprint,
                List.of(
                        new PostingDraft(sourceId, PostingDirection.DEBIT, new BigDecimal("25.00"), "USD", "source"),
                        new PostingDraft(destinationId, PostingDirection.CREDIT, new BigDecimal("25.00"), "USD", "destination")));
    }

    private JournalTransaction journal(String key, String fingerprint) {
        return JournalTransaction.builder()
                .journalId(UUID.randomUUID())
                .journalReference("JRN-" + UUID.randomUUID())
                .journalType(JournalType.TRANSFER)
                .currency("USD")
                .effectiveDate(LocalDate.now())
                .correlationId("correlation-1")
                .createdBy("user-1")
                .createdAt(LocalDateTime.now())
                .idempotencyScope("user-1:TRANSFER")
                .idempotencyKey(key)
                .requestFingerprint(fingerprint)
                .build();
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

    private JournalPosting posting(UUID journalId, UUID accountId, int sequence, PostingDirection direction) {
        return JournalPosting.builder()
                .postingId(UUID.randomUUID())
                .journalId(journalId)
                .ledgerAccountId(accountId)
                .postingSequence(sequence)
                .direction(direction)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build();
    }

    private JournalStateEvent state(UUID journalId, int sequence, JournalState state) {
        return JournalStateEvent.builder()
                .eventId(UUID.randomUUID())
                .journalId(journalId)
                .eventSequence(sequence)
                .state(state)
                .actor("worker")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
