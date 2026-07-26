package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.AccountCreateRequest;
import com.suhasan.finance.account_service.dto.AccountMetadataUpdateRequest;
import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.dto.BalanceOperationRequest;
import com.suhasan.finance.account_service.dto.BalanceOperationResponse;
import com.suhasan.finance.account_service.dto.DebitHoldRequest;
import com.suhasan.finance.account_service.dto.DebitHoldResponse;
import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.dto.LedgerProjectionUpdateRequest;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountBalanceOperation;
import com.suhasan.finance.account_service.entity.AccountBalanceOperationId;
import com.suhasan.finance.account_service.entity.AccountDebitHold;
import com.suhasan.finance.account_service.entity.AccountStatus;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.CreditCardAccount;
import com.suhasan.finance.account_service.entity.SavingsAccount;
import com.suhasan.finance.account_service.entity.BalanceOperationStatus;
import com.suhasan.finance.account_service.entity.DebitHoldStatus;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.mapper.AccountMapper;
import com.suhasan.finance.account_service.repository.AccountBalanceOperationRepository;
import com.suhasan.finance.account_service.repository.AccountDebitHoldRepository;
import com.suhasan.finance.account_service.repository.AccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
@Slf4j
@SuppressWarnings("PMD.AvoidDuplicateLiterals") // Consistent not-found text is part of the API error contract.
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountBalanceOperationRepository balanceOperationRepository;
    private final AccountDebitHoldRepository debitHoldRepository;
    private final AccountMapper accountMapper;
    private final MeterRegistry meterRegistry;
    private NotificationService notificationService;

    private Counter createdCounter;
    private Timer creationTimer;

    public AccountService(final AccountRepository accountRepository,
                          final AccountBalanceOperationRepository balanceOperationRepository,
                          final AccountDebitHoldRepository debitHoldRepository,
                          final AccountMapper accountMapper,
                          final MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.balanceOperationRepository = balanceOperationRepository;
        this.debitHoldRepository = debitHoldRepository;
        this.accountMapper = accountMapper;
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    @Autowired
    void setNotificationService(final NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private void initMetrics() {
        this.createdCounter = meterRegistry.counter("account_created_count");
        this.creationTimer = meterRegistry.timer("account_creation_latency");
        Gauge.builder("account_total_count", accountRepository, AccountRepository::count)
                .description("Total number of accounts")
                .register(meterRegistry);
    }

    public Account create(final Account account) {
        account.setBalance(BigDecimal.ZERO);
        account.setLedgerBalance(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setPendingBalance(BigDecimal.ZERO);
        if (account.getStatus() == null) {
            account.setStatus(AccountStatus.ACTIVE);
        }
        normalizeBalances(account);
        if (account.getCurrency() == null || account.getCurrency().isBlank()) {
            account.setCurrency("USD");
        }
        return creationTimer.record(() -> {
            final Account saved = accountRepository.save(account);
            createdCounter.increment();
            return saved;
        });
    }

    public Account create(final AccountCreateRequest request, final String ownerId) {
        final Account account = switch (request.accountType().trim().toUpperCase(Locale.ROOT)) {
            case "CHECKING" -> new CheckingAccount();
            case "SAVINGS" -> {
                final SavingsAccount savings = new SavingsAccount();
                savings.setInterestRate(request.interestRate() == null ? 0D : request.interestRate());
                yield savings;
            }
            case "CREDIT" -> {
                if (request.creditLimit() == null || request.dueDate() == null) {
                    throw new IllegalArgumentException("Credit limit and due date are required");
                }
                final CreditCardAccount credit = new CreditCardAccount();
                credit.setCreditLimit(request.creditLimit());
                credit.setDueDate(request.dueDate());
                yield credit;
            }
            default -> throw new IllegalArgumentException("Unsupported account type");
        };
        account.setOwnerId(ownerId);
        account.setCurrency(request.currency() == null ? "USD" : request.currency());
        return create(account);
    }
    @Transactional(readOnly = true)
    public Account findById(final Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    public Account updateMetadata(final Long id, final AccountMetadataUpdateRequest updated) {
        final Account existing = findById(id);
        if (existing.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed accounts cannot be updated");
        }
        if (existing instanceof SavingsAccount savings && updated.interestRate() != null) {
            savings.setInterestRate(updated.interestRate());
        }
        if (existing instanceof CreditCardAccount credit) {
            if (updated.creditLimit() != null) credit.setCreditLimit(updated.creditLimit());
            if (updated.dueDate() != null) credit.setDueDate(updated.dueDate());
        }
        return accountRepository.save(existing);
    }

    /** Compatibility API which is deliberately metadata-only. */
    public Account update(final Long id, final Account updated) {
        final Double interestRate = updated instanceof SavingsAccount savings ? savings.getInterestRate() : null;
        final BigDecimal creditLimit = updated instanceof CreditCardAccount credit ? credit.getCreditLimit() : null;
        final java.time.LocalDate dueDate = updated instanceof CreditCardAccount credit ? credit.getDueDate() : null;
        return updateMetadata(id, new AccountMetadataUpdateRequest(interestRate, creditLimit, dueDate));
    }
    public Account updateStatus(final Long id, final AccountStatus status, final String reason, final String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Status reason is required");
        }
        final Account existing = findById(id);
        if (status == AccountStatus.CLOSED || existing.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed lifecycle transitions require the closure coordinator");
        }
        existing.setStatus(status);
        existing.setStatusReason(reason.trim());
        existing.setStatusUpdatedAt(LocalDateTime.now());
        existing.setStatusUpdatedBy(actor);
        final Account saved = accountRepository.save(existing);
        emitAccountStatusNotification(saved);
        return saved;
    }

    public Account close(final Long id, final String reason, final String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Closure reason is required");
        }
        final Account account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
        normalizeBalances(account);
        if (account.getLedgerBalance().signum() != 0
                || account.getAvailableBalance().signum() != 0
                || account.getPendingBalance().signum() != 0) {
            throw new IllegalStateException("Account closure requires zero posted, available, and pending balances");
        }
        if (debitHoldRepository.existsByAccountIdAndStatus(id, DebitHoldStatus.PLACED)) {
            throw new IllegalStateException("Account closure requires no active debit holds");
        }
        account.setStatus(AccountStatus.CLOSED);
        account.setStatusReason(reason.trim());
        account.setStatusUpdatedAt(LocalDateTime.now());
        account.setStatusUpdatedBy(actor);
        return accountRepository.save(account);
    }
    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccounts(final String ownerId, final String accountType, final AccountStatus status, final Pageable pageable) {
        final Page<Account> page;
        if (ownerId != null && accountType != null && status != null) {
            page = accountRepository.findByOwnerIdAndAccountTypeAndStatus(ownerId, accountType, status, pageable);
        } else if (ownerId != null && accountType != null) {
            page = accountRepository.findByOwnerIdAndAccountType(ownerId, accountType, pageable);
        } else if (ownerId != null && status != null) {
            page = accountRepository.findByOwnerIdAndStatus(ownerId, status, pageable);
        } else if (ownerId != null) {
            page = accountRepository.findByOwnerId(ownerId, pageable);
        } else if (accountType != null && status != null) {
            page = accountRepository.findByAccountTypeAndStatus(accountType, status, pageable);
        } else if (accountType != null) {
            page = accountRepository.findByAccountType(accountType, pageable);
        } else if (status != null) {
            page = accountRepository.findByStatus(status, pageable);
        } else {
            page = accountRepository.findAll(pageable);
        }
        return page.map(accountMapper::toDto);
    }


    public AccountResponse applyLedgerProjection(final Long accountId, final LedgerProjectionUpdateRequest request) {
        final Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);
        if (!account.getCurrency().equals(request.currency())) {
            throw new IllegalArgumentException("Projection currency " + request.currency()
                    + " does not match account currency " + account.getCurrency());
        }

        final long currentVersion = account.getLedgerProjectionVersion();
        if (request.version() < currentVersion) {
            return accountMapper.toDto(account);
        }
        if (request.version() == currentVersion) {
            requireExactProjectionReplay(account, request);
            return accountMapper.toDto(account);
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed accounts cannot receive newer ledger projections");
        }

        account.setLedgerBalance(request.postedBalance());
        account.setBalance(request.postedBalance());
        account.setPendingBalance(request.pendingBalance());
        account.setAvailableBalance(request.availableBalance());
        account.setLedgerProjectionVersion(request.version());
        account.setLedgerProjectionSourceEventId(request.sourceEventId());
        account.setLedgerProjectionSyncedAt(request.updatedAt());
        return accountMapper.toDto(accountRepository.save(account));
    }

    private void requireExactProjectionReplay(final Account account, final LedgerProjectionUpdateRequest request) {
        final boolean exact = account.getLedgerBalance().compareTo(request.postedBalance()) == 0
                && account.getPendingBalance().compareTo(request.pendingBalance()) == 0
                && account.getAvailableBalance().compareTo(request.availableBalance()) == 0
                && java.util.Objects.equals(account.getLedgerProjectionSourceEventId(), request.sourceEventId());
        if (!exact) {
            throw new IllegalArgumentException("Projection payload conflicts with projection version "
                    + request.version());
        }
    }

    public BalanceOperationResponse applyBalanceOperation(final Long accountId, final BalanceOperationRequest request) {
        final AccountBalanceOperationId operationId = new AccountBalanceOperationId(request.getOperationId(), accountId);
        final AccountBalanceOperation existingOperation = balanceOperationRepository.findById(operationId).orElse(null);
        if (existingOperation != null) {
            final Account account = findById(accountId);
            normalizeBalances(account);
            return BalanceOperationResponse.builder()
                    .accountId(accountId)
                    .operationId(request.getOperationId())
                    .applied(existingOperation.isApplied())
                    .newBalance(existingOperation.getResultingBalance())
                    .version(account.getVersion())
                    .status(BalanceOperationStatus.REPLAYED)
                    .build();
        }

        final Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        normalizeBalances(account);
        final BigDecimal currentBalance = account.getLedgerBalance();
        final BigDecimal newBalance = currentBalance.add(request.getDelta());
        final BigDecimal newAvailableBalance = account.getAvailableBalance().add(request.getDelta());
        final boolean allowNegative = Boolean.TRUE.equals(request.getAllowNegative());

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed accounts cannot receive balance operations");
        }
        if (account.getStatus() == AccountStatus.FROZEN && request.getDelta().compareTo(BigDecimal.ZERO) < 0) {
            final AccountBalanceOperation rejectedOperation = AccountBalanceOperation.builder()
                    .id(operationId)
                    .transactionId(request.getTransactionId())
                    .delta(request.getDelta())
                    .reason(request.getReason())
                    .allowNegative(allowNegative)
                    .applied(false)
                    .resultingBalance(currentBalance)
                    .status(BalanceOperationStatus.REJECTED)
                    .build();
            balanceOperationRepository.save(rejectedOperation);

            return BalanceOperationResponse.builder()
                    .accountId(accountId)
                    .operationId(request.getOperationId())
                    .applied(false)
                    .newBalance(currentBalance)
                    .version(account.getVersion())
                    .status(BalanceOperationStatus.REJECTED)
                    .message("Account is frozen and cannot be debited")
                    .build();
        }

        if (!allowNegative && newAvailableBalance.compareTo(BigDecimal.ZERO) < 0) {
            final AccountBalanceOperation rejectedOperation = AccountBalanceOperation.builder()
                    .id(operationId)
                    .transactionId(request.getTransactionId())
                    .delta(request.getDelta())
                    .reason(request.getReason())
                    .allowNegative(false)
                    .applied(false)
                    .resultingBalance(currentBalance)
                    .status(BalanceOperationStatus.REJECTED)
                    .build();
            balanceOperationRepository.save(rejectedOperation);

            return BalanceOperationResponse.builder()
                    .accountId(accountId)
                    .operationId(request.getOperationId())
                    .applied(false)
                    .newBalance(currentBalance)
                    .version(account.getVersion())
                    .status(BalanceOperationStatus.REJECTED)
                    .build();
        }

        account.setLedgerBalance(newBalance);
        account.setAvailableBalance(newAvailableBalance);
        account.setBalance(newBalance);
        final Account savedAccount = accountRepository.save(account);
        final AccountBalanceOperation appliedOperation = AccountBalanceOperation.builder()
                .id(operationId)
                .transactionId(request.getTransactionId())
                .delta(request.getDelta())
                .reason(request.getReason())
                .allowNegative(allowNegative)
                .applied(true)
                .resultingBalance(newBalance)
                .status(BalanceOperationStatus.APPLIED)
                .build();
        balanceOperationRepository.save(appliedOperation);

        return BalanceOperationResponse.builder()
                .accountId(accountId)
                .operationId(request.getOperationId())
                .applied(true)
                .newBalance(newBalance)
                .version(savedAccount.getVersion())
                .status(BalanceOperationStatus.APPLIED)
                .build();
    }

    public DebitHoldResponse placeDebitHold(final Long accountId, final DebitHoldRequest request) {
        final AccountDebitHold existing = debitHoldRepository.findById(request.getHoldId()).orElse(null);
        if (existing != null) {
            validateReplayMatchesRequest(accountId, request, existing);
            final Account account = findById(accountId);
            normalizeBalances(account);
            return holdResponse(existing, account, existing.getStatus() == DebitHoldStatus.PLACED);
        }

        final Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);

        if (account.getStatus() == AccountStatus.FROZEN) {
            return rejectedHoldResponse(request.getHoldId(), account, "Account is frozen and cannot be debited");
        }
        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            return rejectedHoldResponse(request.getHoldId(), account, "Insufficient available balance");
        }

        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        final Account savedAccount = accountRepository.save(account);
        final AccountDebitHold hold = AccountDebitHold.builder()
                .holdId(request.getHoldId())
                .accountId(accountId)
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(DebitHoldStatus.PLACED)
                .build();
        debitHoldRepository.save(hold);
        return holdResponse(hold, savedAccount, true);
    }

    public DebitHoldResponse captureDebitHold(final Long accountId, final String holdId, final String transactionId, final String reason) {
        final Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);
        final AccountDebitHold hold = debitHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Debit hold not found: " + holdId));
        validateHoldAccount(accountId, hold);
        if (hold.getStatus() == DebitHoldStatus.CAPTURED) {
            return holdResponse(hold, account, true);
        }
        if (hold.getStatus() != DebitHoldStatus.PLACED) {
            return holdResponse(hold, account, false, "Debit hold is not active");
        }

        account.setLedgerBalance(account.getLedgerBalance().subtract(hold.getAmount()));
        account.setBalance(account.getLedgerBalance());
        final Account savedAccount = accountRepository.save(account);
        hold.setStatus(DebitHoldStatus.CAPTURED);
        hold.setCapturedAt(LocalDateTime.now());
        hold.setCapturedByTransactionId(transactionId);
        hold.setReason(reason != null ? reason : hold.getReason());
        debitHoldRepository.save(hold);
        return holdResponse(hold, savedAccount, true);
    }

    public DebitHoldResponse releaseDebitHold(final Long accountId, final String holdId, final String transactionId, final String reason) {
        final Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);
        final AccountDebitHold hold = debitHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Debit hold not found: " + holdId));
        validateHoldAccount(accountId, hold);
        if (hold.getStatus() == DebitHoldStatus.RELEASED) {
            return holdResponse(hold, account, true);
        }
        if (hold.getStatus() != DebitHoldStatus.PLACED) {
            return holdResponse(hold, account, false, "Debit hold is not active");
        }

        account.setAvailableBalance(account.getAvailableBalance().add(hold.getAmount()));
        final Account savedAccount = accountRepository.save(account);
        hold.setStatus(DebitHoldStatus.RELEASED);
        hold.setReleasedAt(LocalDateTime.now());
        hold.setReleasedByTransactionId(transactionId);
        hold.setReleaseReason(reason);
        debitHoldRepository.save(hold);
        return holdResponse(hold, savedAccount, true);
    }

    private void validateHoldAccount(final Long accountId, final AccountDebitHold hold) {
        if (!accountId.equals(hold.getAccountId())) {
            throw new IllegalArgumentException("Debit hold does not belong to account: " + accountId);
        }
    }

    private DebitHoldResponse rejectedHoldResponse(final String holdId, final Account account, final String message) {
        return DebitHoldResponse.builder()
                .holdId(holdId)
                .accountId(account.getId())
                .applied(false)
                .ledgerBalance(account.getLedgerBalance())
                .availableBalance(account.getAvailableBalance())
                .version(account.getVersion())
                .status(null)
                .message(message)
                .build();
    }

    private DebitHoldResponse holdResponse(final AccountDebitHold hold, final Account account, final boolean applied) {
        return holdResponse(hold, account, applied, null);
    }

    private DebitHoldResponse holdResponse(final AccountDebitHold hold, final Account account, final boolean applied, final String message) {
        return DebitHoldResponse.builder()
                .holdId(hold.getHoldId())
                .accountId(account.getId())
                .applied(applied)
                .ledgerBalance(account.getLedgerBalance())
                .availableBalance(account.getAvailableBalance())
                .version(account.getVersion())
                .status(hold.getStatus())
                .message(message)
                .build();
    }

    private void normalizeBalances(final Account account) {
        if (account.getLedgerBalance() == null) {
            account.setLedgerBalance(account.getBalance());
        }
        if (account.getAvailableBalance() == null) {
            account.setAvailableBalance(account.getLedgerBalance());
        }
        account.setBalance(account.getLedgerBalance());
    }

    private void validateReplayMatchesRequest(final Long accountId, final DebitHoldRequest request, final AccountDebitHold existing) {
        if (!accountId.equals(existing.getAccountId())
                || !request.getTransactionId().equals(existing.getTransactionId())
                || request.getAmount().compareTo(existing.getAmount()) != 0) {
            throw new IllegalArgumentException("Debit hold replay does not match original request: " + request.getHoldId());
        }
    }

    private void emitAccountStatusNotification(final Account account) {
        if (notificationService == null || account.getStatus() == null) {
            return;
        }
        final NotificationType type;
        final NotificationSeverity severity;
        final String title;
        if (account.getStatus() == AccountStatus.FROZEN) {
            type = NotificationType.ACCOUNT_FROZEN;
            severity = NotificationSeverity.CRITICAL;
            title = "Account frozen";
        } else if (account.getStatus() == AccountStatus.ACTIVE) {
            type = NotificationType.ACCOUNT_UNFROZEN;
            severity = NotificationSeverity.SUCCESS;
            title = "Account unfrozen";
        } else {
            return;
        }
        try {
            notificationService.createInternal(NotificationCreateRequest.builder()
                    .userId(account.getOwnerId())
                    .type(type)
                    .severity(severity)
                    .title(title)
                    .message(account.getStatusReason())
                    .sourceType(NotificationSourceType.ACCOUNT)
                    .sourceId(String.valueOf(account.getId()))
                    .dedupeKey("account-status:%s:%s:%s".formatted(account.getId(), account.getStatus(), account.getStatusUpdatedAt()))
                    .build());
        } catch (RuntimeException e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to create account status notification for account {}: {}", account.getId(), e.getMessage());
            }
        }
    }
}
