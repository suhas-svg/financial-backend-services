// package com.suhasan.finance.account_service.service;

// import com.suhasan.finance.account_service.entity.Account;
// import com.suhasan.finance.account_service.repository.AccountRepository;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;
// import java.util.List;

// @Service
// @Transactional
// public class AccountService {
//     private final AccountRepository repo;
//     public AccountService(AccountRepository repo) {
//         this.repo = repo;
//     }

//     public Account create(Account account) {
//         return repo.save(account);
//     }

//     public Account findById(Long id) {
//         return repo.findById(id)
//                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
//     }

//     public List<Account> findAll() {
//         return repo.findAll();
//     }

//     public Account update(Long id, Account updated) {
//         Account existing = findById(id);
//         existing.setBalance(updated.getBalance());
//         // if Savings/Credit, copy interestRate or creditLimit/dueDate as needed
//         return repo.save(existing);
//     }

//     public void delete(Long id) {
//         repo.deleteById(id);
//     }
// }
// package com.suhasan.finance.account_service.service;

// import com.suhasan.finance.account_service.dto.AccountResponse;
// import com.suhasan.finance.account_service.entity.Account;
// import com.suhasan.finance.account_service.mapper.AccountMapper;
// import com.suhasan.finance.account_service.repository.AccountRepository;
// import lombok.RequiredArgsConstructor;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.Pageable;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.util.List;

// @Service
// @Transactional
// @RequiredArgsConstructor
// public class AccountService {
//     private final AccountRepository repo;
//     private final AccountMapper mapper;

//     public Account create(Account account) {
//         return repo.save(account);
//     }

//     public Account findById(Long id) {
//         return repo.findById(id)
//                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
//     }

//     public List<Account> findAll() {
//         return repo.findAll();
//     }

//     public Account update(Long id, Account updated) {
//         Account existing = findById(id);
//         existing.setBalance(updated.getBalance());
//         // if Savings/Credit, copy interestRate or creditLimit/dueDate as needed
//         return repo.save(existing);
//     }

//     public void delete(Long id) {
//         repo.deleteById(id);
//     }

//     /**
//      * Step 5: Pagination & Filtering
//      *
//      * Returns a page of AccountResponse, optionally filtering by ownerId or accountType,
//      * and always applying the provided Pageable for paging & sorting.
//      */
//     public Page<AccountResponse> listAccounts(
//         String ownerId,
//         String accountType,
//         Pageable pageable
//     ) {
//         Page<Account> page;
//         if (ownerId != null) {
//             page = repo.findByOwnerId(ownerId, pageable);
//         } else if (accountType != null) {
//             page = repo.findByAccountType(accountType, pageable);
//         } else {
//             page = repo.findAll(pageable);
//         }
//         return page.map(mapper::toDto);
//     }
// }

// package com.suhasan.finance.account_service.service;

// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.Pageable;
// import com.suhasan.finance.account_service.dto.AccountResponse;
// import com.suhasan.finance.account_service.entity.Account;
// import com.suhasan.finance.account_service.mapper.AccountMapper;
// import com.suhasan.finance.account_service.repository.AccountRepository;
// import io.micrometer.core.instrument.Counter;
// import io.micrometer.core.instrument.Gauge;
// import io.micrometer.core.instrument.MeterRegistry;
// import io.micrometer.core.instrument.Timer;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.util.List;

// @Service
// @Transactional
// public class AccountService {

//     private final AccountRepository repo;
//     private final AccountMapper    mapper;

//     // Micrometer metrics
//     private final Counter createdCounter;
//     private final Timer   creationTimer;
//     private final Gauge   openAccountsGauge;

//     public AccountService(AccountRepository repo,
//                           AccountMapper mapper,
//                           MeterRegistry registry) {
//         this.repo = repo;
//         this.mapper = mapper;

//         // initialize custom business metrics
//         this.createdCounter = registry.counter("account_created_count");
//         this.creationTimer  = registry.timer("account_creation_latency");
//         this.openAccountsGauge = Gauge.builder(
//                 "account_open_total",
//                 this,
//                 svc -> svc.countOpenAccounts()
//             )
//             .description("Total number of open accounts")
//             .register(registry);
//     }

//     public Account create(Account account) {
//         // record timing and increment counter
//         return creationTimer.record(() -> {
//             Account saved = repo.save(account);
//             createdCounter.increment();
//             return saved;
//         });
//     }

//     public Account findById(Long id) {
//         return repo.findById(id)
//                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
//     }

//     public List<Account> findAll() {
//         return repo.findAll();
//     }

//     public Account update(Long id, Account updated) {
//         Account existing = findById(id);
//         existing.setBalance(updated.getBalance());
//         // if Savings/Credit, copy interestRate or creditLimit/dueDate as needed
//         return repo.save(existing);
//     }

//     public void delete(Long id) {
//         repo.deleteById(id);
//     }

//     public Page<AccountResponse> listAccounts(
//         String ownerId,
//         String accountType,
//         Pageable pageable
//     ) {
//         Page<Account> page;
//         if (ownerId != null) {
//             page = repo.findByOwnerId(ownerId, pageable);
//         } else if (accountType != null) {
//             page = repo.findByAccountType(accountType, pageable);
//         } else {
//             page = repo.findAll(pageable);
//         }
//         return page.map(mapper::toDto);
//     }

//     /**
//      * Counts open accounts for usage by gauge metric
//      */
//     private int countOpenAccounts() {
//         // implement repository method countByStatus or similar to fetch open accounts count
//         return (int) repo.countByStatus("OPEN");
//     }
// }


package com.suhasan.finance.account_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.mapper.AccountMapper;
import com.suhasan.finance.account_service.repository.AccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AccountService {

    private final AccountRepository repo;
    private final AccountMapper    mapper;

    // Micrometer metrics
    private final Counter createdCounter;
    private final Timer   creationTimer;
    private final Gauge   totalAccountsGauge;

    public AccountService(AccountRepository repo,
                          AccountMapper mapper,
                          MeterRegistry registry) {
        this.repo   = repo;
        this.mapper = mapper;

        // initialize custom business metrics
        this.createdCounter     = registry.counter("account_created_count");
        this.creationTimer      = registry.timer("account_creation_latency");
        // Gauge for total number of accounts
        this.totalAccountsGauge = Gauge.builder(
                "account_total_count",  // metric name
                repo,                     // call repo.count()
                AccountRepository::count
            )
            .description("Total number of accounts")
            .register(registry);
    }

    public Account create(Account account) {
        // record timing and increment counter
        return creationTimer.record(() -> {
            Account saved = repo.save(account);
            createdCounter.increment();
            return saved;
        });
    }

    public Account findById(Long id) {
        return repo.findById(id)
                   .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    public List<Account> findAll() {
        return repo.findAll();
    }

    public Account update(Long id, Account updated) {
        Account existing = findById(id);
        existing.setBalance(updated.getBalance());
        // if Savings/Credit, copy interestRate or creditLimit/dueDate as needed
        return repo.save(existing);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    public Page<AccountResponse> listAccounts(
        String ownerId,
        String accountType,
        Pageable pageable
    ) {
        Page<Account> page;
        if (ownerId != null) {
            page = repo.findByOwnerId(ownerId, pageable);
        } else if (accountType != null) {
            page = repo.findByAccountType(accountType, pageable);
        } else {
            page = repo.findAll(pageable);
        }
        return page.map(mapper::toDto);
    }

    /**
     * Update account balance (for Transaction Service integration)
     */
    public void updateBalance(Long id, java.math.BigDecimal newBalance) {
        Account existing = findById(id);
        existing.setBalance(newBalance);
        repo.save(existing);
    }
}
