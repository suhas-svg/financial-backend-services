package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.domain.*;
import com.suhasan.finance.transaction_service.ledger.repository.JournalPostingRepository;
import com.suhasan.finance.transaction_service.ledger.repository.JournalStateEventRepository;
import com.suhasan.finance.transaction_service.ledger.repository.JournalTransactionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DefaultCustomerLedgerQueryService implements CustomerLedgerQueryService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerBalanceProjectionRepository projectionRepository;
    private final JournalTransactionRepository journalRepository;
    private final JournalPostingRepository postingRepository;
    private final JournalStateEventRepository stateEventRepository;

    public DefaultCustomerLedgerQueryService(
            LedgerAccountRepository accountRepository,
            LedgerBalanceProjectionRepository projectionRepository,
            JournalTransactionRepository journalRepository,
            JournalPostingRepository postingRepository,
            JournalStateEventRepository stateEventRepository) {
        this.accountRepository = accountRepository;
        this.projectionRepository = projectionRepository;
        this.journalRepository = journalRepository;
        this.postingRepository = postingRepository;
        this.stateEventRepository = stateEventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAccountSummaryResponse> listAccounts(String customerId) {
        List<LedgerAccount> accounts = accountRepository
                .findByOwnerIdAndAccountKindOrderByExternalAccountIdAsc(customerId, LedgerAccountKind.CUSTOMER);
        Map<UUID, LedgerBalanceProjection> projections = projectionMap(accounts.stream()
                .map(LedgerAccount::getLedgerAccountId)
                .toList());
        return accounts.stream()
                .map(account -> toSummary(account, requiredProjection(account.getLedgerAccountId(), projections)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerAccountSummaryResponse getBalance(String customerId, String externalAccountId) {
        LedgerAccount account = accountRepository.findByExternalAccountId(externalAccountId)
                .filter(candidate -> candidate.getAccountKind() == LedgerAccountKind.CUSTOMER)
                .filter(candidate -> customerId.equals(candidate.getOwnerId()))
                .orElseThrow(() -> new LedgerAccountNotFoundException("Ledger account not found"));
        LedgerBalanceProjection projection = projectionRepository.findById(account.getLedgerAccountId())
                .orElseThrow(() -> new LedgerAccountNotFoundException("Ledger projection not found"));
        return toSummary(account, projection);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerAccountSummaryResponse> getBalances(String customerId, List<String> externalAccountIds) {
        if (externalAccountIds == null) {
            return List.of();
        }
        return externalAccountIds.stream()
                .map(externalAccountId -> getBalance(customerId, externalAccountId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerJournalResponse getJournal(String customerId, UUID journalId) {
        JournalTransaction journal = journalRepository.findById(journalId)
                .orElseThrow(() -> new LedgerAccountNotFoundException("Journal not found"));
        List<JournalPosting> postings = postingRepository.findByJournalIdOrderByPostingSequence(journalId);
        Map<UUID, LedgerAccount> accounts = accountMap(postings.stream()
                .map(JournalPosting::getLedgerAccountId)
                .toList());
        List<CustomerJournalPostingResponse> customerPostings = postings.stream()
                .filter(posting -> isOwnedCustomerPosting(customerId, accounts.get(posting.getLedgerAccountId())))
                .map(posting -> toCustomerPosting(posting, accounts.get(posting.getLedgerAccountId())))
                .toList();
        if (customerPostings.isEmpty()) {
            throw new LedgerAccountNotFoundException("Journal not found");
        }
        JournalStateEvent state = stateEventRepository.findFirstByJournalIdOrderByEventSequenceDesc(journalId)
                .orElseThrow(() -> new LedgerAccountNotFoundException("Journal state not found"));
        BigDecimal customerAmount = postings.stream()
                .filter(posting -> isOwnedCustomerPosting(customerId, accounts.get(posting.getLedgerAccountId())))
                .map(JournalPosting::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CustomerJournalResponse(
                journal.getJournalId(),
                journal.getJournalReference(),
                journal.getJournalType().name(),
                state.getState().name(),
                journal.getCurrency(),
                customerAmount,
                journal.getDescription(),
                state.getCreatedAt(),
                journal.getReversalOfJournalId(),
                customerPostings);
    }

    private Map<UUID, LedgerBalanceProjection> projectionMap(Collection<UUID> ledgerAccountIds) {
        return projectionRepository.findAllById(ledgerAccountIds).stream()
                .collect(Collectors.toMap(LedgerBalanceProjection::getLedgerAccountId, Function.identity()));
    }

    private Map<UUID, LedgerAccount> accountMap(Collection<UUID> ledgerAccountIds) {
        Map<UUID, LedgerAccount> accounts = new HashMap<>();
        accountRepository.findAllById(ledgerAccountIds)
                .forEach(account -> accounts.put(account.getLedgerAccountId(), account));
        return accounts;
    }

    private LedgerBalanceProjection requiredProjection(
            UUID ledgerAccountId,
            Map<UUID, LedgerBalanceProjection> projections) {
        LedgerBalanceProjection projection = projections.get(ledgerAccountId);
        if (projection == null) {
            throw new LedgerAccountNotFoundException("Ledger projection not found");
        }
        return projection;
    }

    private LedgerAccountSummaryResponse toSummary(
            LedgerAccount account,
            LedgerBalanceProjection projection) {
        return new LedgerAccountSummaryResponse(
                account.getExternalAccountId(),
                account.getCurrency(),
                projection.getPostedBalance(),
                projection.getPendingBalance(),
                projection.getAvailableBalance(),
                projection.getProjectionVersion(),
                projection.getUpdatedAt());
    }

    private boolean isOwnedCustomerPosting(String customerId, LedgerAccount account) {
        return account != null
                && account.getAccountKind() == LedgerAccountKind.CUSTOMER
                && customerId.equals(account.getOwnerId());
    }

    private CustomerJournalPostingResponse toCustomerPosting(
            JournalPosting posting,
            LedgerAccount account) {
        return new CustomerJournalPostingResponse(
                account.getExternalAccountId(),
                posting.getDirection().name(),
                posting.getAmount(),
                posting.getCurrency(),
                posting.getMemo());
    }
}
