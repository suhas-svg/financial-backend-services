package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.AccountStatus;
import com.suhasan.finance.account_service.dto.BalanceOperationRequest;
import com.suhasan.finance.account_service.dto.BalanceOperationResponse;
import com.suhasan.finance.account_service.dto.DebitHoldRequest;
import com.suhasan.finance.account_service.dto.DebitHoldResponse;
import com.suhasan.finance.account_service.entity.DebitHoldStatus;
import com.suhasan.finance.account_service.mapper.AccountMapper;
import com.suhasan.finance.account_service.repository.AccountBalanceOperationRepository;
import com.suhasan.finance.account_service.repository.AccountDebitHoldRepository;
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
    private AccountDebitHoldRepository debitHoldRepository;

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

        accountService = new AccountService(accountRepository, balanceOperationRepository, debitHoldRepository, accountMapper, meterRegistry);

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
        assertThat(result.getLedgerBalance()).isEqualByComparingTo("1000.00");
        assertThat(result.getAvailableBalance()).isEqualByComparingTo("1000.00");
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
        assertThat(testAccount.getLedgerBalance()).isEqualByComparingTo("1025.00");
        assertThat(testAccount.getAvailableBalance()).isEqualByComparingTo("1025.00");
    }

    @Test
    @DisplayName("Should place debit hold against available balance only")
    void shouldPlaceDebitHoldAgainstAvailableBalanceOnly() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DebitHoldResponse response = accountService.placeDebitHold(1L, DebitHoldRequest.builder()
                .holdId("hold-1")
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .reason("WITHDRAWAL")
                .build());

        assertThat(response.isApplied()).isTrue();
        assertThat(response.getStatus()).isEqualTo(DebitHoldStatus.PLACED);
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Should capture debit hold against ledger balance only")
    void shouldCaptureDebitHoldAgainstLedgerBalanceOnly() {
        testAccount.setAvailableBalance(BigDecimal.valueOf(750));
        com.suhasan.finance.account_service.entity.AccountDebitHold hold = com.suhasan.finance.account_service.entity.AccountDebitHold.builder()
                .holdId("hold-1")
                .accountId(1L)
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .status(DebitHoldStatus.PLACED)
                .build();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DebitHoldResponse response = accountService.captureDebitHold(1L, "hold-1", "tx-1", "WITHDRAWAL_CAPTURE");

        assertThat(response.isApplied()).isTrue();
        assertThat(response.getStatus()).isEqualTo(DebitHoldStatus.CAPTURED);
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("750.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("Should release debit hold back to available balance")
    void shouldReleaseDebitHoldBackToAvailableBalance() {
        testAccount.setAvailableBalance(BigDecimal.valueOf(750));
        com.suhasan.finance.account_service.entity.AccountDebitHold hold = com.suhasan.finance.account_service.entity.AccountDebitHold.builder()
                .holdId("hold-1")
                .accountId(1L)
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .status(DebitHoldStatus.PLACED)
                .build();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DebitHoldResponse response = accountService.releaseDebitHold(1L, "hold-1", "tx-1", "WITHDRAWAL_RELEASE");

        assertThat(response.isApplied()).isTrue();
        assertThat(response.getStatus()).isEqualTo(DebitHoldStatus.RELEASED);
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Should reject debit hold when available balance is insufficient")
    void shouldRejectDebitHoldWhenAvailableBalanceIsInsufficient() {
        testAccount.setAvailableBalance(BigDecimal.valueOf(50));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.empty());

        DebitHoldResponse response = accountService.placeDebitHold(1L, DebitHoldRequest.builder()
                .holdId("hold-1")
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .reason("WITHDRAWAL")
                .build());

        assertThat(response.isApplied()).isFalse();
        assertThat(response.getStatus()).isNull();
        assertThat(response.getMessage()).isEqualTo("Insufficient available balance");
    }

    @Test
    @DisplayName("Should reject debit hold for frozen account")
    void shouldRejectDebitHoldForFrozenAccount() {
        testAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.empty());

        DebitHoldResponse response = accountService.placeDebitHold(1L, DebitHoldRequest.builder()
                .holdId("hold-1")
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .reason("WITHDRAWAL")
                .build());

        assertThat(response.isApplied()).isFalse();
        assertThat(response.getStatus()).isNull();
        assertThat(response.getMessage()).isEqualTo("Account is frozen and cannot be debited");
    }

    @Test
    @DisplayName("Should return existing hold for matching place replay")
    void shouldReturnExistingHoldForMatchingPlaceReplay() {
        testAccount.setAvailableBalance(BigDecimal.valueOf(750));
        com.suhasan.finance.account_service.entity.AccountDebitHold hold = com.suhasan.finance.account_service.entity.AccountDebitHold.builder()
                .holdId("hold-1")
                .accountId(1L)
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .status(DebitHoldStatus.PLACED)
                .build();
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        DebitHoldResponse response = accountService.placeDebitHold(1L, DebitHoldRequest.builder()
                .holdId("hold-1")
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .reason("WITHDRAWAL")
                .build());

        assertThat(response.isApplied()).isTrue();
        assertThat(response.getStatus()).isEqualTo(DebitHoldStatus.PLACED);
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("750.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should reject mismatched place replay")
    void shouldRejectMismatchedPlaceReplay() {
        com.suhasan.finance.account_service.entity.AccountDebitHold hold = com.suhasan.finance.account_service.entity.AccountDebitHold.builder()
                .holdId("hold-1")
                .accountId(1L)
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .status(DebitHoldStatus.PLACED)
                .build();
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> accountService.placeDebitHold(2L, DebitHoldRequest.builder()
                .holdId("hold-1")
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .reason("WITHDRAWAL")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debit hold replay does not match original request");
    }

    @Test
    @DisplayName("Should return captured hold for duplicate capture")
    void shouldReturnCapturedHoldForDuplicateCapture() {
        testAccount.setLedgerBalance(BigDecimal.valueOf(750));
        testAccount.setAvailableBalance(BigDecimal.valueOf(750));
        com.suhasan.finance.account_service.entity.AccountDebitHold hold = com.suhasan.finance.account_service.entity.AccountDebitHold.builder()
                .holdId("hold-1")
                .accountId(1L)
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .status(DebitHoldStatus.CAPTURED)
                .build();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));

        DebitHoldResponse response = accountService.captureDebitHold(1L, "hold-1", "tx-1", "WITHDRAWAL_CAPTURE");

        assertThat(response.isApplied()).isTrue();
        assertThat(response.getStatus()).isEqualTo(DebitHoldStatus.CAPTURED);
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("750.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should return released hold for duplicate release")
    void shouldReturnReleasedHoldForDuplicateRelease() {
        testAccount.setAvailableBalance(BigDecimal.valueOf(1000));
        com.suhasan.finance.account_service.entity.AccountDebitHold hold = com.suhasan.finance.account_service.entity.AccountDebitHold.builder()
                .holdId("hold-1")
                .accountId(1L)
                .transactionId("tx-1")
                .amount(BigDecimal.valueOf(250))
                .status(DebitHoldStatus.RELEASED)
                .build();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testAccount));
        when(debitHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));

        DebitHoldResponse response = accountService.releaseDebitHold(1L, "hold-1", "tx-1", "WITHDRAWAL_RELEASE");

        assertThat(response.isApplied()).isTrue();
        assertThat(response.getStatus()).isEqualTo(DebitHoldStatus.RELEASED);
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("1000.00");
        verify(accountRepository, never()).save(any(Account.class));
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
