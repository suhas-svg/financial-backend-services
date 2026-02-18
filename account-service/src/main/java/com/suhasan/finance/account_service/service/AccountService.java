package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.dto.BalanceOperationRequest;
import com.suhasan.finance.account_service.dto.BalanceOperationResponse;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountBalanceOperation;
import com.suhasan.finance.account_service.entity.AccountBalanceOperationId;
import com.suhasan.finance.account_service.entity.BalanceOperationStatus;
import com.suhasan.finance.account_service.mapper.AccountMapper;
import com.suhasan.finance.account_service.repository.AccountBalanceOperationRepository;
import com.suhasan.finance.account_service.repository.AccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountBalanceOperationRepository balanceOperationRepository;
    private final AccountMapper accountMapper;
    private final MeterRegistry meterRegistry;

    private Counter createdCounter;
    private Timer creationTimer;

    public AccountService(AccountRepository accountRepository,
                          AccountBalanceOperationRepository balanceOperationRepository,
                          AccountMapper accountMapper,
                          MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.balanceOperationRepository = balanceOperationRepository;
        this.accountMapper = accountMapper;
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    private void initMetrics() {
        this.createdCounter = meterRegistry.counter("account_created_count");
        this.creationTimer = meterRegistry.timer("account_creation_latency");
        Gauge.builder("account_total_count", accountRepository, AccountRepository::count)
                .description("Total number of accounts")
                .register(meterRegistry);
    }

    public Account create(Account account) {
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
        return accountRepository.save(existing);
    }

    public void delete(Long id) {
        accountRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccounts(String ownerId, String accountType, Pageable pageable) {
        Page<Account> page;
        if (ownerId != null && accountType != null) {
            page = accountRepository.findByOwnerIdAndAccountType(ownerId, accountType, pageable);
        } else if (ownerId != null) {
            page = accountRepository.findByOwnerId(ownerId, pageable);
        } else if (accountType != null) {
            page = accountRepository.findByAccountType(accountType, pageable);
        } else {
            page = accountRepository.findAll(pageable);
        }
        return page.map(accountMapper::toDto);
    }

    public void updateBalance(Long id, BigDecimal newBalance) {
        Account existing = findById(id);
        existing.setBalance(newBalance);
        accountRepository.save(existing);
    }

    public BalanceOperationResponse applyBalanceOperation(Long accountId, BalanceOperationRequest request) {
        AccountBalanceOperationId operationId = new AccountBalanceOperationId(request.getOperationId(), accountId);
        AccountBalanceOperation existingOperation = balanceOperationRepository.findById(operationId).orElse(null);
        if (existingOperation != null) {
            Account account = findById(accountId);
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

        BigDecimal currentBalance = account.getBalance();
        BigDecimal newBalance = currentBalance.add(request.getDelta());
        boolean allowNegative = Boolean.TRUE.equals(request.getAllowNegative());

        if (!allowNegative && newBalance.compareTo(BigDecimal.ZERO) < 0) {
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
}
