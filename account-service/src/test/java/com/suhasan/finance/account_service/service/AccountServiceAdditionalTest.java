package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.AccountCreateRequest;
import com.suhasan.finance.account_service.dto.AccountMetadataUpdateRequest;
import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.dto.BalanceOperationRequest;
import com.suhasan.finance.account_service.dto.DebitHoldRequest;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountBalanceOperation;
import com.suhasan.finance.account_service.entity.AccountBalanceOperationId;
import com.suhasan.finance.account_service.entity.AccountDebitHold;
import com.suhasan.finance.account_service.entity.AccountStatus;
import com.suhasan.finance.account_service.entity.BalanceOperationStatus;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.entity.CreditCardAccount;
import com.suhasan.finance.account_service.entity.DebitHoldStatus;
import com.suhasan.finance.account_service.entity.SavingsAccount;
import com.suhasan.finance.account_service.mapper.AccountMapper;
import com.suhasan.finance.account_service.repository.AccountBalanceOperationRepository;
import com.suhasan.finance.account_service.repository.AccountDebitHoldRepository;
import com.suhasan.finance.account_service.repository.AccountRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceAdditionalTest {
    private AccountRepository accounts;
    private AccountBalanceOperationRepository operations;
    private AccountDebitHoldRepository holds;
    private AccountMapper mapper;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        operations = mock(AccountBalanceOperationRepository.class);
        holds = mock(AccountDebitHoldRepository.class);
        mapper = mock(AccountMapper.class);
        service = new AccountService(accounts, operations, holds, mapper, new SimpleMeterRegistry());
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsEverySupportedMetadataShapeAtZeroAndRejectsUnsupportedCreditRequests() {
        Account checking = service.create(new AccountCreateRequest(
                " checking ", "ignored", null, null, null, null), "owner");
        assertThat(checking).isInstanceOf(CheckingAccount.class);
        assertThat(checking.getOwnerId()).isEqualTo("owner");
        assertThat(checking.getCurrency()).isEqualTo("USD");
        assertThat(checking.getBalance()).isEqualByComparingTo("0");

        SavingsAccount savings = (SavingsAccount) service.create(new AccountCreateRequest(
                "SAVINGS", null, "EUR", null, null, null), "owner");
        assertThat(savings.getInterestRate()).isZero();
        SavingsAccount rated = (SavingsAccount) service.create(new AccountCreateRequest(
                "SAVINGS", null, "USD", 2.5, null, null), "owner");
        assertThat(rated.getInterestRate()).isEqualTo(2.5);

        LocalDate due = LocalDate.now().plusDays(10);
        CreditCardAccount credit = (CreditCardAccount) service.create(new AccountCreateRequest(
                "CREDIT", null, "USD", null, new BigDecimal("500"), due), "owner");
        assertThat(credit.getCreditLimit()).isEqualByComparingTo("500");
        assertThat(credit.getDueDate()).isEqualTo(due);
        assertThatThrownBy(() -> service.create(new AccountCreateRequest(
                "CREDIT", null, "USD", null, null, due), "owner"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new AccountCreateRequest(
                "BROKERAGE", null, "USD", null, null, null), "owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatesOnlyTypeSpecificMetadataAndRejectsClosedAccounts() {
        SavingsAccount savings = new SavingsAccount();
        savings.setId(1L);
        savings.setStatus(AccountStatus.ACTIVE);
        when(accounts.findById(1L)).thenReturn(Optional.of(savings));
        assertThat(((SavingsAccount) service.updateMetadata(
                1L, new AccountMetadataUpdateRequest(3.1, null, null))).getInterestRate()).isEqualTo(3.1);

        CreditCardAccount credit = new CreditCardAccount();
        credit.setId(2L);
        credit.setStatus(AccountStatus.ACTIVE);
        LocalDate due = LocalDate.now().plusDays(20);
        when(accounts.findById(2L)).thenReturn(Optional.of(credit));
        service.updateMetadata(2L, new AccountMetadataUpdateRequest(null, new BigDecimal("900"), due));
        assertThat(credit.getCreditLimit()).isEqualByComparingTo("900");
        assertThat(credit.getDueDate()).isEqualTo(due);
        service.update(2L, credit);

        CheckingAccount closed = account(3L, AccountStatus.CLOSED, "0");
        when(accounts.findById(3L)).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> service.updateMetadata(
                3L, new AccountMetadataUpdateRequest(null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closesOnlyZeroBalanceAccountsWithoutActiveHolds() {
        assertThatThrownBy(() -> service.close(1L, " ", "admin")).isInstanceOf(IllegalArgumentException.class);
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.close(1L, "requested", "admin"))
                .isInstanceOf(IllegalArgumentException.class);

        CheckingAccount funded = account(2L, AccountStatus.ACTIVE, "1");
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(funded));
        assertThatThrownBy(() -> service.close(2L, "requested", "admin"))
                .hasMessageContaining("zero posted");

        CheckingAccount activeHold = account(3L, AccountStatus.ACTIVE, "0");
        when(accounts.findByIdForUpdate(3L)).thenReturn(Optional.of(activeHold));
        when(holds.existsByAccountIdAndStatus(3L, DebitHoldStatus.PLACED)).thenReturn(true);
        assertThatThrownBy(() -> service.close(3L, "requested", "admin"))
                .hasMessageContaining("active debit holds");

        CheckingAccount closable = account(4L, AccountStatus.ACTIVE, "0");
        when(accounts.findByIdForUpdate(4L)).thenReturn(Optional.of(closable));
        Account result = service.close(4L, " requested ", "admin");
        assertThat(result.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(result.getStatusReason()).isEqualTo("requested");
    }

    @Test
    void selectsEveryAccountListQueryCombination() {
        var page = PageRequest.of(0, 10);
        var result = new PageImpl<Account>(List.of(account(1L, AccountStatus.ACTIVE, "0")));
        when(accounts.findByOwnerIdAndAccountTypeAndStatus("owner", "CHECKING", AccountStatus.ACTIVE, page)).thenReturn(result);
        when(accounts.findByOwnerIdAndAccountType("owner", "CHECKING", page)).thenReturn(result);
        when(accounts.findByOwnerIdAndStatus("owner", AccountStatus.ACTIVE, page)).thenReturn(result);
        when(accounts.findByOwnerId("owner", page)).thenReturn(result);
        when(accounts.findByAccountTypeAndStatus("CHECKING", AccountStatus.ACTIVE, page)).thenReturn(result);
        when(accounts.findByAccountType("CHECKING", page)).thenReturn(result);
        when(accounts.findByStatus(AccountStatus.ACTIVE, page)).thenReturn(result);
        when(accounts.findAll(page)).thenReturn(result);
        when(mapper.toDto(any(Account.class))).thenReturn(new AccountResponse());

        assertThat(service.listAccounts("owner", "CHECKING", AccountStatus.ACTIVE, page)).hasSize(1);
        assertThat(service.listAccounts("owner", "CHECKING", null, page)).hasSize(1);
        assertThat(service.listAccounts("owner", null, AccountStatus.ACTIVE, page)).hasSize(1);
        assertThat(service.listAccounts("owner", null, null, page)).hasSize(1);
        assertThat(service.listAccounts(null, "CHECKING", AccountStatus.ACTIVE, page)).hasSize(1);
        assertThat(service.listAccounts(null, "CHECKING", null, page)).hasSize(1);
        assertThat(service.listAccounts(null, null, AccountStatus.ACTIVE, page)).hasSize(1);
        assertThat(service.listAccounts(null, null, null, page)).hasSize(1);
    }

    @Test
    void replaysBalanceOperationsAndRejectsMissingClosedOrInsufficientAccounts() {
        var id = new AccountBalanceOperationId("op", 1L);
        when(operations.findById(id)).thenReturn(Optional.of(AccountBalanceOperation.builder()
                .id(id).applied(true).resultingBalance(new BigDecimal("12")).status(BalanceOperationStatus.APPLIED)
                .build()));
        when(accounts.findById(1L)).thenReturn(Optional.of(account(1L, AccountStatus.ACTIVE, "12")));
        assertThat(service.applyBalanceOperation(1L, operation("op", "-1", false)).getStatus())
                .isEqualTo(BalanceOperationStatus.REPLAYED);

        when(operations.findById(new AccountBalanceOperationId("missing", 2L))).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.applyBalanceOperation(2L, operation("missing", "1", false)))
                .isInstanceOf(IllegalArgumentException.class);

        when(operations.findById(new AccountBalanceOperationId("closed", 3L))).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account(3L, AccountStatus.CLOSED, "10")));
        assertThatThrownBy(() -> service.applyBalanceOperation(3L, operation("closed", "1", false)))
                .isInstanceOf(IllegalStateException.class);

        when(operations.findById(new AccountBalanceOperationId("overdraw", 4L))).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(4L)).thenReturn(Optional.of(account(4L, AccountStatus.ACTIVE, "10")));
        assertThat(service.applyBalanceOperation(4L, operation("overdraw", "-11", false)).isApplied()).isFalse();
    }

    @Test
    void coversHoldMissingOwnershipReplayAndTerminalStateBranches() {
        when(holds.findById("new")).thenReturn(Optional.empty());
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.placeDebitHold(1L, holdRequest("new")))
                .isInstanceOf(IllegalArgumentException.class);

        CheckingAccount account = account(2L, AccountStatus.ACTIVE, "100");
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(account));
        when(holds.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.captureDebitHold(2L, "missing", "tx", null))
                .isInstanceOf(IllegalArgumentException.class);

        AccountDebitHold other = hold("other", 99L, DebitHoldStatus.PLACED);
        when(holds.findById("other")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.captureDebitHold(2L, "other", "tx", null))
                .hasMessageContaining("does not belong");

        AccountDebitHold captured = hold("captured", 2L, DebitHoldStatus.CAPTURED);
        when(holds.findById("captured")).thenReturn(Optional.of(captured));
        assertThat(service.captureDebitHold(2L, "captured", "tx", null).isApplied()).isTrue();
        assertThat(service.releaseDebitHold(2L, "captured", "tx", null).isApplied()).isFalse();

        AccountDebitHold released = hold("released", 2L, DebitHoldStatus.RELEASED);
        when(holds.findById("released")).thenReturn(Optional.of(released));
        assertThat(service.releaseDebitHold(2L, "released", "tx", null).isApplied()).isTrue();
        assertThat(service.captureDebitHold(2L, "released", "tx", null).isApplied()).isFalse();
    }

    @Test
    void emitsStatusNotificationsForFreezeAndUnfreezeAndContainsNotificationFailure() {
        NotificationService notifications = mock(NotificationService.class);
        service.setNotificationService(notifications);
        CheckingAccount account = account(1L, AccountStatus.ACTIVE, "0");
        account.setOwnerId("owner");
        when(accounts.findById(1L)).thenReturn(Optional.of(account));

        service.updateStatus(1L, AccountStatus.FROZEN, "fraud", "admin");
        service.updateStatus(1L, AccountStatus.ACTIVE, "cleared", "admin");
        service.updateStatus(1L, null, "review", "admin");
        verify(notifications, org.mockito.Mockito.times(2)).createInternal(any());

        doThrow(new IllegalStateException("notification unavailable")).when(notifications).createInternal(any());
        assertThat(service.updateStatus(1L, AccountStatus.FROZEN, "again", "admin")).isSameAs(account);
        assertThatThrownBy(() -> service.updateStatus(1L, AccountStatus.CLOSED, "bad", "admin"))
                .isInstanceOf(IllegalStateException.class);
    }

    private CheckingAccount account(long id, AccountStatus status, String balance) {
        CheckingAccount account = new CheckingAccount();
        account.setId(id);
        account.setStatus(status);
        account.setBalance(new BigDecimal(balance));
        account.setLedgerBalance(new BigDecimal(balance));
        account.setAvailableBalance(new BigDecimal(balance));
        account.setPendingBalance(BigDecimal.ZERO);
        return account;
    }

    private BalanceOperationRequest operation(String id, String delta, boolean allowNegative) {
        return BalanceOperationRequest.builder().operationId(id).transactionId("tx")
                .delta(new BigDecimal(delta)).reason("test").allowNegative(allowNegative).build();
    }

    private DebitHoldRequest holdRequest(String id) {
        return DebitHoldRequest.builder().holdId(id).transactionId("tx")
                .amount(BigDecimal.ONE).reason("test").build();
    }

    private AccountDebitHold hold(String id, long accountId, DebitHoldStatus status) {
        return AccountDebitHold.builder().holdId(id).accountId(accountId).transactionId("tx")
                .amount(BigDecimal.TEN).status(status).build();
    }
}
