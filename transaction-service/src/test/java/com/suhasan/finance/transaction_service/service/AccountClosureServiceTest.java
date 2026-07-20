package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.client.ResilientAccountServiceClient;
import com.suhasan.finance.transaction_service.dto.AccountDto;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountStatus;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerBalanceProjectionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerProjectionOutboxRepository;
import com.suhasan.finance.transaction_service.ledger.service.AccountLedgerResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountClosureServiceTest {
    private JdbcTemplate jdbc;
    private ResilientAccountServiceClient accountClient;
    private AccountLedgerResolver accountLedgerResolver;
    private LedgerAccountRepository ledgerAccounts;
    private LedgerBalanceProjectionRepository projections;
    private LedgerProjectionOutboxRepository outbox;
    private AccountClosureService service;
    private UUID ledgerAccountId;
    private LedgerAccount ledgerAccount;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        accountClient = mock(ResilientAccountServiceClient.class);
        accountLedgerResolver = mock(AccountLedgerResolver.class);
        ledgerAccounts = mock(LedgerAccountRepository.class);
        projections = mock(LedgerBalanceProjectionRepository.class);
        outbox = mock(LedgerProjectionOutboxRepository.class);
        service = new AccountClosureService(jdbc, accountClient, accountLedgerResolver, ledgerAccounts, projections, outbox);
        ledgerAccountId = UUID.randomUUID();
        ledgerAccount = LedgerAccount.builder()
                .ledgerAccountId(ledgerAccountId)
                .externalAccountId("42")
                .ownerId("customer-1")
                .accountKind(LedgerAccountKind.CUSTOMER)
                .currency("USD")
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        when(accountClient.getAccountInternal("42")).thenReturn(AccountDto.builder()
                .id(42L).ownerId("customer-1").status("ACTIVE").build());
        when(accountLedgerResolver.resolveCustomerAccount(eq("42"), any(AccountDto.class))).thenReturn(ledgerAccountId);
        when(ledgerAccounts.findByExternalAccountId("42")).thenReturn(Optional.of(ledgerAccount));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(0L);
    }

    @Test
    void resolvesUntouchedAccountThenClosesOnlyAfterLockingTheAuthoritativeZeroProjection() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(ledgerAccountId, BigDecimal.ZERO);
        AccountDto closed = AccountDto.builder().id(42L).ownerId("customer-1").status("CLOSED").build();
        when(projections.lockAllOrdered(List.of(ledgerAccountId))).thenReturn(List.of(projection));
        when(outbox.countByExternalAccountIdAndDeliveredAtIsNull("42")).thenReturn(0L);
        when(accountClient.closeAccount("42", "requested")).thenReturn(closed);

        AccountDto result = service.close("42", "customer-1", false, "requested");

        assertThat(result.getStatus()).isEqualTo("CLOSED");
        assertThat(ledgerAccount.getStatus()).isEqualTo(LedgerAccountStatus.CLOSED);
        verify(accountLedgerResolver).resolveCustomerAccount(eq("42"), any(AccountDto.class));
        verify(ledgerAccounts).save(ledgerAccount);
    }

    @Test
    void rejectsStaleZeroAccountProjectionWhenAuthoritativeLedgerIsFunded() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(ledgerAccountId, new BigDecimal("25.00"));
        when(projections.lockAllOrdered(List.of(ledgerAccountId))).thenReturn(List.of(projection));

        assertThatThrownBy(() -> service.close("42", "customer-1", false, "requested"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zero posted");

        verify(accountClient, never()).closeAccount(anyString(), anyString());
    }

    @Test
    void rejectsClosureWhileAProjectionDeliveryIsPending() {
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(ledgerAccountId, BigDecimal.ZERO);
        when(projections.lockAllOrdered(List.of(ledgerAccountId))).thenReturn(List.of(projection));
        when(outbox.countByExternalAccountIdAndDeliveredAtIsNull("42")).thenReturn(1L);

        assertThatThrownBy(() -> service.close("42", "customer-1", false, "requested"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projection is synchronized");

        verify(accountClient, never()).closeAccount(anyString(), anyString());
    }
}