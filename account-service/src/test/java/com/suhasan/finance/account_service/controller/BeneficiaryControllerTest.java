package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.BeneficiaryCreateRequest;
import com.suhasan.finance.account_service.dto.BeneficiaryResponse;
import com.suhasan.finance.account_service.dto.BeneficiaryUpdateRequest;
import com.suhasan.finance.account_service.entity.BeneficiaryStatus;
import com.suhasan.finance.account_service.service.BeneficiaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BeneficiaryControllerTest {

    private BeneficiaryService service;
    private BeneficiaryController controller;

    @BeforeEach
    void setUp() {
        service = mock(BeneficiaryService.class);
        controller = new BeneficiaryController(service);
    }

    @Test
    @DisplayName("Creates beneficiary for authenticated customer")
    void createsForAuthenticatedCustomer() {
        BeneficiaryCreateRequest request = BeneficiaryCreateRequest.builder()
                .displayName("Rent")
                .destinationAccountId("200")
                .currency("USD")
                .build();
        BeneficiaryResponse response = response("beneficiary-1");
        when(service.create("customer", request)).thenReturn(response);

        var result = controller.create(request, auth("customer"));

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody()).isSameAs(response);
        verify(service).create("customer", request);
    }

    @Test
    @DisplayName("Lists beneficiaries for authenticated customer")
    void listsForAuthenticatedCustomer() {
        when(service.list("customer", BeneficiaryStatus.ACTIVE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(response("beneficiary-1"))));

        var result = controller.list(BeneficiaryStatus.ACTIVE, PageRequest.of(0, 20), auth("customer"));

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).extracting(BeneficiaryResponse::getBeneficiaryId)
                .containsExactly("beneficiary-1");
        verify(service).list("customer", BeneficiaryStatus.ACTIVE, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("Updates beneficiary for authenticated customer")
    void updatesForAuthenticatedCustomer() {
        BeneficiaryUpdateRequest request = BeneficiaryUpdateRequest.builder()
                .displayName("Updated")
                .build();
        BeneficiaryResponse response = response("beneficiary-1");
        when(service.update("beneficiary-1", "customer", request)).thenReturn(response);

        var result = controller.update("beneficiary-1", request, auth("customer"));

        assertThat(result.getBody()).isSameAs(response);
        verify(service).update("beneficiary-1", "customer", request);
    }

    @Test
    @DisplayName("Disables beneficiary for authenticated customer")
    void disablesForAuthenticatedCustomer() {
        BeneficiaryResponse response = response("beneficiary-1");
        when(service.disable("beneficiary-1", "customer")).thenReturn(response);

        var result = controller.disable("beneficiary-1", auth("customer"));

        assertThat(result.getBody()).isSameAs(response);
        verify(service).disable("beneficiary-1", "customer");
    }

    private BeneficiaryResponse response(String id) {
        return BeneficiaryResponse.builder()
                .beneficiaryId(id)
                .userId("customer")
                .displayName("Rent")
                .destinationAccountId("200")
                .currency("USD")
                .status(BeneficiaryStatus.ACTIVE)
                .build();
    }

    private TestingAuthenticationToken auth(String name) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(name, "token", "ROLE_USER");
        authentication.setAuthenticated(true);
        return authentication;
    }
}
