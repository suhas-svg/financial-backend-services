package com.suhasan.finance.account_service.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InrCurrencyValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsInrForAccountAndBeneficiaryInputs() {
        AccountRequest account = new AccountRequest();
        account.setAccountType("CHECKING");
        account.setOwnerId("customer-1");
        account.setBalance(new BigDecimal("123456.78"));
        account.setCurrency("INR");

        BeneficiaryCreateRequest beneficiary = BeneficiaryCreateRequest.builder()
                .displayName("Rent")
                .destinationAccountId("200")
                .currency("INR")
                .build();

        assertThat(validator.validate(account)).isEmpty();
        assertThat(validator.validate(beneficiary)).isEmpty();
    }
}
