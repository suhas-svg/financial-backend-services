package com.suhasan.finance.account_service.service;

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

@Service
@Transactional
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountBalanceOperationRepository balanceOperationRepository;
    private final AccountDebitHoldRepository debitHoldRepository;
    private final AccountMapper accountMapper;
    private final MeterRegistry meterRegistry;
    private NotificationService notificationService;

    private Counter createdCounter;
    private Timer creationTimer;

    public AccountService(AccountRepository accountRepository,
                          AccountBalanceOperationRepository balanceOperationRepository,
                          AccountDebitHoldRepository debitHoldRepository,
                          AccountMapper accountMapper,
                          MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.balanceOperationRepository = balanceOperationRepository;
        this.debitHoldRepository = debitHoldRepository;
        this.accountMapper = accountMapper;
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    @Autowired
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private void initMetrics() {
        this.createdCounter = meterRegistry.counter("account_created_count");
        this.creationTimer = meterRegistry.timer("account_creation_latency");
        Gauge.builder("account_total_count", accountRepository, AccountRepository::count)
                .description("Total number of accounts")
                .register(meterRegistry);
    }

    public Account create(Account account) {
        if (account.getStatus() == null) {
            account.setStatus(AccountStatus.ACTIVE);
        }
        normalizeBalances(account);
        if (account.getCurrency() == null || account.getCurrency().isBlank()) {
            account.setCurrency("USD");
        }
        return creationTimer.record(() -> {
            Account saved = accountRepository.save(account);
            createdCounter.increment();
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    public Account update(Long id, Account updated) {
        Account existing = findById(id);
        existing.setBalance(updated.getBalance());
        existing.setLedgerBalance(updated.getBalance());
        existing.setAvailableBalance(updated.getBalance());
        return accountRepository.save(existing);
    }

    public Account updateStatus(Long id, AccountStatus status, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Status reason is required");
        }
        Account existing = findById(id);
        existing.setStatus(status);
        existing.setStatusReason(reason.trim());
        existing.setStatusUpdatedAt(LocalDateTime.now());
        existing.setStatusUpdatedBy(actor);
        Account saved = accountRepository.save(existing);
        emitAccountStatusNotification(saved);
        return saved;
    }

    public void delete(Long id) {
        accountRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccounts(String ownerId, String accountType, AccountStatus status, Pageable pageable) {
        Page<Account> page;
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

    public void updateBalance(Long id, BigDecimal newBalance) {
        Account existing = findById(id);
        existing.setBalance(newBalance);
        existing.setLedgerBalance(newBalance);
        existing.setAvailableBalance(newBalance);
        accountRepository.save(existing);
    }

    public AccountResponse applyLedgerProjection(Long accountId, LedgerProjectionUpdateRequest request) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);
        if (!account.getCurrency().equals(request.currency())) {
            throw new IllegalArgumentException("Projection currency " + request.currency()
                    + " does not match account currency " + account.getCurrency());
        }

        long currentVersion = account.getLedgerProjectionVersion();
        if (request.version() < currentVersion) {
            return accountMapper.toDto(account);
        }
        if (request.version() == currentVersion) {
            requireExactProjectionReplay(account, request);
            return accountMapper.toDto(account);
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

    private void requireExactProjectionReplay(Account account, LedgerProjectionUpdateRequest request) {
        boolean exact = account.getLedgerBalance().compareTo(request.postedBalance()) == 0
                && account.getPendingBalance().compareTo(request.pendingBalance()) == 0
                && account.getAvailableBalance().compareTo(request.availableBalance()) == 0
                && java.util.Objects.equals(account.getLedgerProjectionSourceEventId(), request.sourceEventId());
        if (!exact) {
            throw new IllegalArgumentException("Projection payload conflicts with projection version "
                    + request.version());
        }
    }

    public BalanceOperationResponse applyBalanceOperation(Long accountId, BalanceOperationRequest request) {
        AccountBalanceOperationId operationId = new AccountBalanceOperationId(request.getOperationId(), accountId);
        AccountBalanceOperation existingOperation = balanceOperationRepository.findById(operationId).orElse(null);
        if (existingOperation != null) {
            Account account = findById(accountId);
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

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        normalizeBalances(account);
        BigDecimal currentBalance = account.getLedgerBalance();
        BigDecimal newBalance = currentBalance.add(request.getDelta());
        BigDecimal newAvailableBalance = account.getAvailableBalance().add(request.getDelta());
        boolean allowNegative = Boolean.TRUE.equals(request.getAllowNegative());

        if (account.getStatus() == AccountStatus.FROZEN && request.getDelta().compareTo(BigDecimal.ZERO) < 0) {
            AccountBalanceOperation rejectedOperation = AccountBalanceOperation.builder()
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
            AccountBalanceOperation rejectedOperation = AccountBalanceOperation.builder()
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
        Account savedAccount = accountRepository.save(account);
        AccountBalanceOperation appliedOperation = AccountBalanceOperation.builder()
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

    public DebitHoldResponse placeDebitHold(Long accountId, DebitHoldRequest request) {
        AccountDebitHold existing = debitHoldRepository.findById(request.getHoldId()).orElse(null);
        if (existing != null) {
            validateReplayMatchesRequest(accountId, request, existing);
            Account account = findById(accountId);
            normalizeBalances(account);
            return holdResponse(existing, account, existing.getStatus() == DebitHoldStatus.PLACED);
        }

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);

        if (account.getStatus() == AccountStatus.FROZEN) {
            return rejectedHoldResponse(request.getHoldId(), account, "Account is frozen and cannot be debited");
        }
        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            return rejectedHoldResponse(request.getHoldId(), account, "Insufficient available balance");
        }

        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        Account savedAccount = accountRepository.save(account);
        AccountDebitHold hold = AccountDebitHold.builder()
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

    public DebitHoldResponse captureDebitHold(Long accountId, String holdId, String transactionId, String reason) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);
        AccountDebitHold hold = debitHoldRepository.findById(holdId)
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
        Account savedAccount = accountRepository.save(account);
        hold.setStatus(DebitHoldStatus.CAPTURED);
        hold.setCapturedAt(LocalDateTime.now());
        hold.setCapturedByTransactionId(transactionId);
        hold.setReason(reason != null ? reason : hold.getReason());
        debitHoldRepository.save(hold);
        return holdResponse(hold, savedAccount, true);
    }

    public DebitHoldResponse releaseDebitHold(Long accountId, String holdId, String transactionId, String reason) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        normalizeBalances(account);
        AccountDebitHold hold = debitHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Debit hold not found: " + holdId));
        validateHoldAccount(accountId, hold);
        if (hold.getStatus() == DebitHoldStatus.RELEASED) {
            return holdResponse(hold, account, true);
        }
        if (hold.getStatus() != DebitHoldStatus.PLACED) {
            return holdResponse(hold, account, false, "Debit hold is not active");
        }

        account.setAvailableBalance(account.getAvailableBalance().add(hold.getAmount()));
        Account savedAccount = accountRepository.save(account);
        hold.setStatus(DebitHoldStatus.RELEASED);
        hold.setReleasedAt(LocalDateTime.now());
        hold.setReleasedByTransactionId(transactionId);
        hold.setReleaseReason(reason);
        debitHoldRepository.save(hold);
        return holdResponse(hold, savedAccount, true);
    }

    private void validateHoldAccount(Long accountId, AccountDebitHold hold) {
        if (!accountId.equals(hold.getAccountId())) {
            throw new IllegalArgumentException("Debit hold does not belong to account: " + accountId);
        }
    }

    private DebitHoldResponse rejectedHoldResponse(String holdId, Account account, String message) {
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

    private DebitHoldResponse holdResponse(AccountDebitHold hold, Account account, boolean applied) {
        return holdResponse(hold, account, applied, null);
    }

    private DebitHoldResponse holdResponse(AccountDebitHold hold, Account account, boolean applied, String message) {
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

    private void normalizeBalances(Account account) {
        if (account.getLedgerBalance() == null) {
            account.setLedgerBalance(account.getBalance());
        }
        if (account.getAvailableBalance() == null) {
            account.setAvailableBalance(account.getLedgerBalance());
        }
        account.setBalance(account.getLedgerBalance());
    }

    private void validateReplayMatchesRequest(Long accountId, DebitHoldRequest request, AccountDebitHold existing) {
        if (!accountId.equals(existing.getAccountId())
                || !request.getTransactionId().equals(existing.getTransactionId())
                || request.getAmount().compareTo(existing.getAmount()) != 0) {
            throw new IllegalArgumentException("Debit hold replay does not match original request: " + request.getHoldId());
        }
    }

    private void emitAccountStatusNotification(Account account) {
        if (notificationService == null || account.getStatus() == null) {
            return;
        }
        NotificationType type;
        NotificationSeverity severity;
        String title;
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
            log.warn("Failed to create account status notification for account {}: {}", account.getId(), e.getMessage());
        }
    }
}
