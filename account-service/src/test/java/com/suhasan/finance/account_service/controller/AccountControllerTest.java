package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AccountCreateRequest;
import com.suhasan.finance.account_service.dto.AccountMetadataUpdateRequest;
import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.entity.Account;
import com.suhasan.finance.account_service.entity.AccountStatus;
import com.suhasan.finance.account_service.entity.CheckingAccount;
import com.suhasan.finance.account_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerTest {
    private AccountService service;
    private AccountController controller;
    private CheckingAccount account;
    private AccountResponse response;

    @BeforeEach
    void setUp() {
        service = mock(AccountService.class);
        controller = new AccountController(service);
        account = new CheckingAccount();
        account.setId(1L);
        account.setOwnerId("owner");
        response = new AccountResponse();
        response.setId(1L);
        response.setOwnerId("owner");
        when(service.toResponse(account)).thenReturn(response);

    }
    @Test
    void customerListingIsAlwaysScopedWhileAdminAndInternalCanChooseOwner() {
        PageRequest page = PageRequest.of(0, 20);
        when(service.listAccounts("customer", null, null, page))
                .thenReturn(new PageImpl<>(List.of(new AccountResponse())));
        assertThat(controller.listAccounts("someone-else", null, null, page, auth("customer", "ROLE_USER"))
                .getBody()).hasSize(1);
        verify(service).listAccounts("customer", null, null, page);

        controller.listAccounts("chosen", "CHECKING", AccountStatus.ACTIVE, page, auth("admin", "ROLE_ADMIN"));
        verify(service).listAccounts("chosen", "CHECKING", AccountStatus.ACTIVE, page);
        controller.listAccounts("chosen", null, null, page, auth("service", "ROLE_INTERNAL_SERVICE"));
        verify(service).listAccounts("chosen", null, null, page);
    }

    @Test
    void getAndUpdateRequireOwnershipUnlessPrivileged() {
        when(service.findById(1L)).thenReturn(account);
        when(service.updateMetadata(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(account);
        assertThat(controller.get(1L, auth("owner", "ROLE_USER")).getBody()).isSameAs(response);
        assertThatThrownBy(() -> controller.get(1L, auth("other", "ROLE_USER")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(controller.get(1L, auth("admin", "ROLE_ADMIN")).getBody()).isSameAs(response);
        assertThat(controller.get(1L, auth("service", "ROLE_INTERNAL_SERVICE")).getBody()).isSameAs(response);

        AccountMetadataUpdateRequest request = new AccountMetadataUpdateRequest(null, null, null);
        assertThat(controller.update(1L, request, auth("owner", "ROLE_USER")).getBody()).isSameAs(response);
        assertThatThrownBy(() -> controller.update(1L, request, auth("other", "ROLE_USER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createBindsOwnerToCustomerAndAllowsPrivilegedExplicitOwnerWithBlankFallback() {
        AccountCreateRequest customerRequest = new AccountCreateRequest(
                "CHECKING", "attacker", "USD", null, null, null);
        when(service.create(customerRequest, "customer")).thenReturn(account);
        assertThat(controller.create(customerRequest, auth("customer", "ROLE_USER")).getStatusCode().value())
                .isEqualTo(201);
        verify(service).create(customerRequest, "customer");

        AccountCreateRequest adminRequest = new AccountCreateRequest(
                "CHECKING", "chosen", "USD", null, null, null);
        controller.create(adminRequest, auth("admin", "ROLE_ADMIN"));
        verify(service).create(adminRequest, "chosen");

        AccountCreateRequest blankRequest = new AccountCreateRequest(
                "CHECKING", " ", "USD", null, null, null);
        controller.create(blankRequest, auth("service", "ROLE_INTERNAL_SERVICE"));
        verify(service).create(blankRequest, "service");
    }

    private TestingAuthenticationToken auth(String name, String role) {
        return new TestingAuthenticationToken(name, "n/a", role);
    }
}
