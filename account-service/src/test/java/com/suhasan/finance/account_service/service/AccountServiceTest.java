package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.mapper.AccountMapper;
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
        lenient().when(timer.record(any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            java.util.function.Supplier<Account> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        
        accountService = new AccountService(accountRepository, accountMapper, meterRegistry);

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