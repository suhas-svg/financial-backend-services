package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LedgerBootstrapService {

    private static final List<String> DEFAULT_SYSTEM_CURRENCIES = List.of("USD", "EUR", "GBP");

    private static final List<LedgerAccountKind> SYSTEM_ACCOUNT_KINDS = List.of(
            LedgerAccountKind.CLEARING,
            LedgerAccountKind.SUSPENSE,
            LedgerAccountKind.FEE);

    private final LedgerBootstrapAccountSource accountSource;
    private final LedgerAccountRepository accountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerPostingService postingService;
    private final MeterRegistry meterRegistry;

    public LedgerBootstrapService(
            LedgerBootstrapAccountSource accountSource,
            LedgerAccountRepository accountRepository,
            LedgerBalanceProjectionRepository projectionRepository,
            TransactionRepository transactionRepository,
            LedgerPostingService postingService,
            MeterRegistry meterRegistry) {
        this.accountSource = accountSource;
        this.accountRepository = accountRepository;
        this.projectionRepository = projectionRepository;
        this.transactionRepository = transactionRepository;
        this.postingService = postingService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public LedgerBootstrapResult bootstrap(LedgerBootstrapCommand command) {
        requireCutoverWindow(command);
        List<LedgerBootstrapAccountSnapshot> accounts = accountSource.fetchAccountsForBootstrap();
        requireNoLegacyBlockers(accounts);

        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        accounts.forEach(account -> currencies.add(account.currency()));
        if (currencies.isEmpty()) {
            currencies.addAll(DEFAULT_SYSTEM_CURRENCIES);
        }

        SystemSeedResult systemSeedResult = seedSystemAccounts(currencies);
        int importedAccounts = 0;
        int reusedAccounts = 0;
        int openingJournals = 0;

        for (LedgerBootstrapAccountSnapshot account : accounts) {
            Optional<LedgerAccount> existing = accountRepository.findByExternalAccountId(account.externalAccountId());
            if (existing.isPresent()) {
                requireProjectionParity(existing.get(), account);
                reusedAccounts++;
                continue;
            }

            LedgerAccount customerAccount = saveCustomerAccount(account);
            ensureProjection(customerAccount.getLedgerAccountId(), BigDecimal.ZERO);
            importedAccounts++;
            if (account.ledgerBalance().compareTo(BigDecimal.ZERO) != 0) {
                postOpeningJournal(command, account, customerAccount, systemSeedResult.accountsByKindAndCurrency());
                openingJournals++;
            }
        }

        meterRegistry.counter("ledger.bootstrap.runs", "result", "success").increment();
        return new LedgerBootstrapResult(
                importedAccounts,
                reusedAccounts,
                systemSeedResult.seededAccounts(),
                openingJournals,
                List.copyOf(currencies));
    }

    private void requireCutoverWindow(LedgerBootstrapCommand command) {
        if (!command.enabled()) {
            meterRegistry.counter("ledger.bootstrap.runs", "result", "disabled").increment();
            throw new IllegalStateException("Ledger bootstrap is disabled");
        }
        if (!command.maintenanceMode()) {
            meterRegistry.counter("ledger.bootstrap.runs", "result", "not_maintenance").increment();
            throw new IllegalStateException("Ledger bootstrap requires maintenance mode");
        }
    }

    private void requireNoLegacyBlockers(List<LedgerBootstrapAccountSnapshot> accounts) {
        List<String> blockers = new ArrayList<>();
        long accountsWithHolds = accounts.stream()
                .filter(LedgerBootstrapAccountSnapshot::hasUnresolvedLegacyHold)
                .count();
        if (accountsWithHolds > 0) {
            blockers.add(accountsWithHolds + " unresolved legacy holds");
        }
        long processingTransactions = transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PROCESSING).size();
        long pendingTransactions = transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING).size();
        if (processingTransactions + pendingTransactions > 0) {
            blockers.add((processingTransactions + pendingTransactions) + " processing transactions");
        }
        if (!blockers.isEmpty()) {
            meterRegistry.counter("ledger.bootstrap.runs", "result", "blocked").increment();
            throw new IllegalStateException("Ledger bootstrap blocked by " + String.join(" and ", blockers));
        }
    }

    private SystemSeedResult seedSystemAccounts(Collection<String> currencies) {
        int seeded = 0;
        Map<String, LedgerAccount> accountsByKindAndCurrency = new HashMap<>();
        for (String currency : currencies) {
            for (LedgerAccountKind kind : SYSTEM_ACCOUNT_KINDS) {
                Optional<LedgerAccount> existing = accountRepository.findByAccountKindAndCurrency(kind, currency);
                if (existing.isPresent()) {
                    accountsByKindAndCurrency.put(systemKey(kind, currency), existing.get());
                } else {
                    LedgerAccount account = accountRepository.save(LedgerAccount.builder()
                            .ledgerAccountId(UUID.randomUUID())
                            .accountKind(kind)
                            .currency(currency)
                            .status(LedgerAccountStatus.ACTIVE)
                            .createdAt(LocalDateTime.now())
                            .build());
                    accountsByKindAndCurrency.put(systemKey(kind, currency), account);
                    ensureProjection(account.getLedgerAccountId(), BigDecimal.ZERO);
                    seeded++;
                }
            }
        }
        return new SystemSeedResult(seeded, accountsByKindAndCurrency);
    }

    private LedgerAccount saveCustomerAccount(LedgerBootstrapAccountSnapshot snapshot) {
        return accountRepository.save(LedgerAccount.builder()
                .ledgerAccountId(UUID.randomUUID())
                .accountKind(LedgerAccountKind.CUSTOMER)
                .externalAccountId(snapshot.externalAccountId())
                .ownerId(snapshot.ownerId())
                .currency(snapshot.currency())
                .status(statusFor(snapshot.status()))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private LedgerAccountStatus statusFor(String legacyStatus) {
        if ("CLOSED".equalsIgnoreCase(legacyStatus)) {
            return LedgerAccountStatus.CLOSED;
        }
        return LedgerAccountStatus.ACTIVE;
    }

    private void ensureProjection(UUID ledgerAccountId, BigDecimal postedBalance) {
        Optional<LedgerBalanceProjection> existing = projectionRepository.findById(ledgerAccountId);
        if (existing.isEmpty()) {
            projectionRepository.save(LedgerBalanceProjection.open(ledgerAccountId, postedBalance));
        }
    }

    private void requireProjectionParity(LedgerAccount account, LedgerBootstrapAccountSnapshot snapshot) {
        LedgerBalanceProjection projection = projectionRepository.findById(account.getLedgerAccountId())
                .orElseThrow(() -> new IllegalStateException("Ledger projection missing for imported account " + snapshot.externalAccountId()));
        if (projection.getPostedBalance().compareTo(snapshot.ledgerBalance()) != 0) {
            meterRegistry.counter("ledger.bootstrap.runs", "result", "parity_failed").increment();
            throw new IllegalStateException("Ledger bootstrap parity verification failed for " + snapshot.externalAccountId());
        }
    }

    private void postOpeningJournal(
            LedgerBootstrapCommand command,
            LedgerBootstrapAccountSnapshot snapshot,
            LedgerAccount customerAccount,
            Map<String, LedgerAccount> systemAccounts) {
        LedgerAccount clearing = systemAccounts.get(systemKey(LedgerAccountKind.CLEARING, snapshot.currency()));
        if (clearing == null) {
            throw new IllegalStateException("Clearing account missing for " + snapshot.currency());
        }
        BigDecimal absoluteBalance = snapshot.ledgerBalance().abs();
        List<PostingDraft> postings = snapshot.ledgerBalance().compareTo(BigDecimal.ZERO) >= 0
                ? List.of(
                        new PostingDraft(clearing.getLedgerAccountId(), PostingDirection.DEBIT, absoluteBalance, snapshot.currency(), "Opening balance source"),
                        new PostingDraft(customerAccount.getLedgerAccountId(), PostingDirection.CREDIT, absoluteBalance, snapshot.currency(), "Opening balance"))
                : List.of(
                        new PostingDraft(customerAccount.getLedgerAccountId(), PostingDirection.DEBIT, absoluteBalance, snapshot.currency(), "Opening balance"),
                        new PostingDraft(clearing.getLedgerAccountId(), PostingDirection.CREDIT, absoluteBalance, snapshot.currency(), "Opening balance source"));
        JournalCommand journal = new JournalCommand(
                JournalType.OPENING_BALANCE,
                snapshot.currency(),
                command.businessDate(),
                "Ledger bootstrap opening balance for account " + snapshot.externalAccountId(),
                "ledger-bootstrap-" + command.businessDate(),
                command.requestedBy(),
                "LEDGER_BOOTSTRAP:" + command.businessDate(),
                "OPENING:" + snapshot.externalAccountId(),
                snapshot.externalAccountId() + ":" + snapshot.currency() + ":" + snapshot.ledgerBalance(),
                postings);
        JournalResult pending = postingService.createPending(journal);
        postingService.post(pending.journalId(), command.requestedBy());
    }

    private String systemKey(LedgerAccountKind kind, String currency) {
        return kind + ":" + currency;
    }

    private record SystemSeedResult(int seededAccounts, Map<String, LedgerAccount> accountsByKindAndCurrency) {
    }
}
