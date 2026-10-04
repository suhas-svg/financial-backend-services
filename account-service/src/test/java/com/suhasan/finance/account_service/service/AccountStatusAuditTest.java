package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountStatus;
import com.suhasan.finance.account_service.entity.AccountStatusAuditEvent;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.mapper.AccountMapper;
import com.suhasan.finance.account_service.repository.AccountBalanceOperationRepository;
import com.suhasan.finance.account_service.repository.AccountDebitHoldRepository;
import com.suhasan.finance.account_service.repository.AccountRepository;
import com.suhasan.finance.account_service.repository.AccountStatusAuditEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A freeze is a privileged operator action, and the accounts row only ever holds the CURRENT
 * status. Before this audit existed, an unfreeze erased all evidence that a freeze had happened,
 * so the operations console's governance record never showed it. These tests pin the trail.
 */
class AccountStatusAuditTest {

    private AccountRepository accountRepository;
    private AccountStatusAuditEventRepository auditRepository;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        auditRepository = mock(AccountStatusAuditEventRepository.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        service = new AccountService(
                accountRepository,
                mock(AccountBalanceOperationRepository.class),
                mock(AccountDebitHoldRepository.class),
                mock(AccountMapper.class),
                registry);
        service.setStatusAuditRepository(auditRepository);
    }

    private CheckingAccount accountWithStatus(AccountStatus status) {
        CheckingAccount account = new CheckingAccount();
        account.setId(7L);
        account.setOwnerId("walk_cust_a");
        account.setBalance(java.math.BigDecimal.ZERO);
        account.setLedgerBalance(java.math.BigDecimal.ZERO);
        account.setAvailableBalance(java.math.BigDecimal.ZERO);
        account.setPendingBalance(java.math.BigDecimal.ZERO);
        account.setCurrency("USD");
        account.setCreatedAt(LocalDate.now());
        account.setStatus(status);
        return account;
    }

    @Test
    @DisplayName("freezing an account writes an audit row recording the previous status")
    void freezeWritesAuditRow() {
        CheckingAccount account = accountWithStatus(AccountStatus.ACTIVE);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any(AccountStatusAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(7L, AccountStatus.FROZEN, "operator freeze", "walk_admin_1");

        var captor = org.mockito.ArgumentCaptor.forClass(AccountStatusAuditEvent.class);
        verify(auditRepository, times(1)).save(captor.capture());
        AccountStatusAuditEvent event = captor.getValue();
        assertThat(event.getAccountId()).isEqualTo(7L);
        assertThat(event.getPreviousStatus()).isEqualTo("ACTIVE");
        assertThat(event.getNewStatus()).isEqualTo("FROZEN");
        assertThat(event.getReason()).isEqualTo("operator freeze");
        assertThat(event.getActor()).isEqualTo("walk_admin_1");
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("unfreezing records FROZEN -> ACTIVE, so the freeze survives in history")
    void unfreezeRetainsTheFreezeInHistory() {
        CheckingAccount account = accountWithStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any(AccountStatusAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(7L, AccountStatus.ACTIVE, "freeze lifted", "walk_admin_1");

        var captor = org.mockito.ArgumentCaptor.forClass(AccountStatusAuditEvent.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo("FROZEN");
        assertThat(captor.getValue().getNewStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("a rejected status change writes no audit row")
    void blankReasonWritesNoAuditRow() {
        assertThatThrownBy(() -> service.updateStatus(7L, AccountStatus.FROZEN, "  ", "walk_admin_1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Status reason is required");

        verify(auditRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("the operation still succeeds when the audit repository is absent")
    void statusChangeSurvivesMissingAuditRepository() {
        AccountService withoutAudit = new AccountService(
                accountRepository,
                mock(AccountBalanceOperationRepository.class),
                mock(AccountDebitHoldRepository.class),
                mock(AccountMapper.class),
                new SimpleMeterRegistry());

        CheckingAccount account = accountWithStatus(AccountStatus.ACTIVE);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account saved = withoutAudit.updateStatus(7L, AccountStatus.FROZEN, "no audit repo", "walk_admin_1");

        assertThat(saved.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }
}
