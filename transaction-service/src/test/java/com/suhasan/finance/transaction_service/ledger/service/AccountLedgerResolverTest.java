package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class AccountLedgerResolverTest {

    private final LedgerAccountRepository accountRepository = mock(LedgerAccountRepository.class);
    private final LedgerBalanceProjectionRepository projectionRepository = mock(LedgerBalanceProjectionRepository.class);
    private final AccountLedgerResolver resolver = new AccountLedgerResolver(accountRepository, projectionRepository);

    @Test
    void createsCustomerLedgerAccountAndProjectionFromAccountSnapshotWhenMissing() {
        AccountDto account = AccountDto.builder()
                .id(101L)
                .ownerId("customer-1")
                .currency("USD")
                .ledgerBalance(new BigDecimal("25.00"))
                .status("ACTIVE")
                .build();
        when(accountRepository.findByExternalAccountId("101")).thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectionRepository.save(any(LedgerBalanceProjection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID ledgerAccountId = resolver.resolveCustomerAccount("101", account);

        ArgumentCaptor<LedgerAccount> accountCaptor = ArgumentCaptor.forClass(LedgerAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(ledgerAccountId).isEqualTo(accountCaptor.getValue().getLedgerAccountId());
        assertThat(accountCaptor.getValue().getAccountKind()).isEqualTo(LedgerAccountKind.CUSTOMER);
        assertThat(accountCaptor.getValue().getExternalAccountId()).isEqualTo("101");
        assertThat(accountCaptor.getValue().getOwnerId()).isEqualTo("customer-1");
        assertThat(accountCaptor.getValue().getCurrency()).isEqualTo("USD");

        ArgumentCaptor<LedgerBalanceProjection> projectionCaptor = ArgumentCaptor.forClass(LedgerBalanceProjection.class);
        verify(projectionRepository).save(projectionCaptor.capture());
        assertThat(projectionCaptor.getValue().getLedgerAccountId()).isEqualTo(ledgerAccountId);
        assertThat(projectionCaptor.getValue().getPostedBalance()).isEqualByComparingTo("25.00");
    }
}
