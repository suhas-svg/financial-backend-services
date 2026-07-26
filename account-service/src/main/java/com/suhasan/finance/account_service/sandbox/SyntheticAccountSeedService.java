package com.suhasan.finance.account_service.sandbox;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.SyntheticSandboxSeedAccount;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.SyntheticSandboxSeedAccountRepository;
import com.suhasan.finance.account_service.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyntheticAccountSeedService {
    private final SyntheticSandboxGuard guard;
    private final SyntheticSandboxSeedAccountRepository seedRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @Transactional
    public SeededAccounts seed(final String owner) {
        guard.requireSynthetic();
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Seed owner is required");
        final Account zero = account("phase2-zero-v1", owner);
        final Account funded = account("phase2-funded-v1", owner);
        return new SeededAccounts("controlled-beta-phase2-v1", zero.getId().toString(), funded.getId().toString(),
                List.of(zero.getId().toString(), funded.getId().toString()));
    }

    private Account account(final String seedKey, final String owner) {
        return seedRepository.findById(seedKey)
                .flatMap(entry -> accountRepository.findById(entry.getAccountId()))
                .orElseGet(() -> {
                    final CheckingAccount requested = new CheckingAccount();
                    requested.setOwnerId(owner);
                    requested.setCurrency("USD");
                    final Account created = accountService.create(requested);
                    final SyntheticSandboxSeedAccount entry = new SyntheticSandboxSeedAccount();
                    entry.setSeedKey(seedKey);
                    entry.setAccountId(created.getId());
                    entry.setCreatedAt(Instant.now());
                    seedRepository.save(entry);
                    return created;
                });
    }

    public record SeededAccounts(String seedVersion, String zeroAccountId, String fundedAccountId,
                                 List<String> accountIds) {
        public SeededAccounts {
            accountIds = List.copyOf(accountIds);
        }
    }
}
