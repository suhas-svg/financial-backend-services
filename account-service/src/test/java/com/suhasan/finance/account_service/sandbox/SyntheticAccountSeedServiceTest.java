package com.suhasan.finance.account_service.sandbox;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.SyntheticSandboxSeedAccount;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.SyntheticSandboxSeedAccountRepository;
import com.suhasan.finance.account_service.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyntheticAccountSeedServiceTest {
    @Mock SyntheticSandboxGuard guard;
    @Mock SyntheticSandboxSeedAccountRepository seeds;
    @Mock AccountRepository accounts;
    @Mock AccountService accountService;

    @Test
    void createsBothVersionedZeroBalanceAccountsAndReturnsImmutableEvidence() {
        var service = new SyntheticAccountSeedService(guard, seeds, accounts, accountService);
        var zero = account(10L);
        var funded = account(11L);
        when(seeds.findById("phase2-zero-v1")).thenReturn(Optional.empty());
        when(seeds.findById("phase2-funded-v1")).thenReturn(Optional.empty());
        when(accountService.create(any(Account.class))).thenReturn(zero, funded);

        var result = service.seed("sandbox-owner");

        assertThat(result.seedVersion()).isEqualTo("controlled-beta-phase2-v1");
        assertThat(result.zeroAccountId()).isEqualTo("10");
        assertThat(result.fundedAccountId()).isEqualTo("11");
        assertThat(result.accountIds()).containsExactly("10", "11");
        assertThatThrownBy(() -> result.accountIds().add("12"))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(seeds, times(2)).save(any(SyntheticSandboxSeedAccount.class));
    }

    @Test
    void reusesTheRecordedAccountAndRejectsBlankOwners() {
        var service = new SyntheticAccountSeedService(guard, seeds, accounts, accountService);
        var entry = new SyntheticSandboxSeedAccount();
        entry.setAccountId(10L);
        when(seeds.findById("phase2-zero-v1")).thenReturn(Optional.of(entry));
        when(seeds.findById("phase2-funded-v1")).thenReturn(Optional.of(entry));
        when(accounts.findById(10L)).thenReturn(Optional.of(account(10L)));

        assertThat(service.seed("sandbox-owner").accountIds()).containsExactly("10", "10");
        verify(accountService, never()).create(any(Account.class));
        assertThatThrownBy(() -> service.seed(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
    }

    private CheckingAccount account(long id) {
        var account = new CheckingAccount();
        account.setId(id);
        account.setOwnerId("sandbox-owner");
        account.setCurrency("USD");
        return account;
    }
}
