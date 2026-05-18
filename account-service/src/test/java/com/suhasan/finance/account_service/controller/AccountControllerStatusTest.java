package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AccountStatusUpdateRequest;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountStatus;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerStatusTest {

    private AccountService accountService;
    private AccountController controller;
    private Account account;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        controller = new AccountController(accountService);
        account = new CheckingAccount();
        account.setId(1L);
        account.setOwnerId("customer");
        account.setBalance(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("Admin can freeze account with reason")
    void adminCanFreezeAccountWithReason() {
        account.setStatus(AccountStatus.FROZEN);
        when(accountService.updateStatus(1L, AccountStatus.FROZEN, "fraud review", "admin"))
                .thenReturn(account);

        Account result = controller.updateStatus(
                1L,
                new AccountStatusUpdateRequest(AccountStatus.FROZEN, "fraud review"),
                auth("admin", "ROLE_ADMIN")
        ).getBody();

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AccountStatus.FROZEN);
        verify(accountService).updateStatus(1L, AccountStatus.FROZEN, "fraud review", "admin");
    }

    @Test
    @DisplayName("Regular user cannot update account status")
    void regularUserCannotUpdateAccountStatus() {
        assertThatThrownBy(() -> controller.updateStatus(
                1L,
                new AccountStatusUpdateRequest(AccountStatus.FROZEN, "fraud review"),
                auth("customer", "ROLE_USER")
        )).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only admins can update account status");
    }

    @Test
    @DisplayName("Blank status reason is rejected")
    void blankStatusReasonIsRejected() {
        assertThatThrownBy(() -> controller.updateStatus(
                1L,
                new AccountStatusUpdateRequest(AccountStatus.ACTIVE, " "),
                auth("admin", "ROLE_ADMIN")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status reason is required");
    }

    private TestingAuthenticationToken auth(String name, String role) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(name, "token", role);
        authentication.setAuthenticated(true);
        return authentication;
    }
}
