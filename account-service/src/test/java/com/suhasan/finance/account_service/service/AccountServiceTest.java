package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.AccountStatus;
import com.suhasan.finance.account_service.dto.BalanceOperationRequest;
import com.suhasan.finance.account_service.dto.BalanceOperationResponse;
import com.suhasan.finance.account_service.mapper.AccountMapper;
import com.suhasan.finance.account_service.repository.AccountBalanceOperationRepository;
import com.suhasan.finance.account_service.repository.AccountRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Service Unit Tests")
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountBalanceOperationRepository balanceOperationRepository;

    @Mock
    private MeterRegistry meterRegistry;

    private AccountService accountService;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Mock the meter registry components with lenient stubbing
        io.micrometer.core.instrument.Counter counter = mock(io.micrometer.core.instrument.Counter.class);
        io.micrometer.core.instrument.Timer timer = mock(io.micrometer.core.instrument.Timer.class);
        
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
        lenient().when(meterRegistry.timer(anyString())).thenReturn(timer);
        lenient().when(timer.record(org.mockito.ArgumentMatchers.<java.util.function.Supplier<Account>>any())).thenAnswer(invocation -> {
            java.util.function.Supplier<Account> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        
        accountService = new AccountService(accountRepository, balanceOperationRepository, accountMapper, meterRegistry);

        testAccount = new CheckingAccount();
        testAccount.setId(1L);
        testAccount.setBalance(BigDecimal.valueOf(1000.00));
        testAccount.setOwnerId("user123");
    }

    @Test
    @DisplayName("Should create account successfully")
    void shouldCreateAccountSuccessfully() {
        // Given
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // When
        Account result = accountService.create(testAccount);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(accountRepository).save(testAccount);
    }

    @Test
    @DisplayName("Should default new accounts to active status")
    void shouldDefaultNewAccountsToActiveStatus() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.create(testAccount);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should update account status with required reason and actor")
    void shouldUpdateAccountStatusWithReasonAndActor() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.updateStatus(1L, AccountStatus.FROZEN, "fraud review", "admin");

        assertThat(result.getStatus()).isEqualTo(AccountStatus.FROZEN);
        assertThat(result.getStatusReason()).isEqualTo("fraud review");
        assertThat(result.getStatusUpdatedBy()).isEqualTo("admin");
        assertThat(result.getStatusUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject blank status reason")
    void shouldRejectBlankStatusReason() {
        assertThatThrownBy(() -> accountService.updateStatus(1L, AccountStatus.FROZEN, " ", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status reason is required");
    }

    @Test
    @DisplayName("Should reject debit balance operation for frozen account")
    void shouldRejectDebitBalanceOperationForFrozenAccount() {
        testAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));

        BalanceOperationRequest request = BalanceOperationRequest.builder()
                .operationId("op-debit")
                .transactionId("tx-1")
                .delta(BigDecimal.valueOf(-25))
                .reason("WITHDRAWAL")
                .allowNegative(false)
                .build();

        BalanceOperationResponse response = accountService.applyBalanceOperation(1L, request);

        assertThat(response.isApplied()).isFalse();
        assertThat(response.getStatus()).isEqualTo(com.suhasan.finance.account_service.entity.BalanceOperationStatus.REJECTED);
        assertThat(response.getMessage()).isEqualTo("Account is frozen and cannot be debited");
        assertThat(response.getNewBalance()).isEqualByComparingTo("1000.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should allow credit balance operation for frozen account")
    void shouldAllowCreditBalanceOperationForFrozenAccount() {
        testAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BalanceOperationRequest request = BalanceOperationRequest.builder()
                .operationId("op-credit")
                .transactionId("tx-2")
                .delta(BigDecimal.valueOf(25))
                .reason("DEPOSIT")
                .allowNegative(true)
                .build();

        BalanceOperationResponse response = accountService.applyBalanceOperation(1L, request);

        assertThat(response.isApplied()).isTrue();
        assertThat(response.getNewBalance()).isEqualByComparingTo("1025.00");
    }

    @Test
    @DisplayName("Should find account by id successfully")
    void shouldFindAccountByIdSuccessfully() {
        // Given
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        // When
        Account result = accountService.findById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(accountRepository).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when account not found")
    void shouldThrowExceptionWhenAccountNotFound() {
        // Given
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> accountService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found: 999");
    }
}
