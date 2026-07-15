package com.suhasan.finance.transaction_service.dto;

import com.suhasan.finance.transaction_service.entity.ScheduledTransferType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InrMoneyRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsInrAcrossImmediateAndScheduledMoneyInputs() {
        DepositRequest deposit = DepositRequest.builder()
                .accountId("10").amount(new BigDecimal("100000.05")).currency("INR").build();
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .accountId("10").amount(new BigDecimal("500.05")).currency("INR").build();
        TransferRequest transfer = TransferRequest.builder()
                .fromAccountId("10").toAccountId("11")
                .amount(new BigDecimal("2500.05")).currency("INR").build();
        ScheduledTransferCreateRequest schedule = new ScheduledTransferCreateRequest();
        schedule.setFromAccountId("10");
        schedule.setToAccountId("11");
        schedule.setAmount(new BigDecimal("2500.05"));
        schedule.setCurrency("INR");
        schedule.setScheduleType(ScheduledTransferType.ONE_TIME);
        schedule.setFirstRunAt(Instant.now().plusSeconds(3600));

        assertThat(validator.validate(deposit)).isEmpty();
        assertThat(validator.validate(withdrawal)).isEmpty();
        assertThat(validator.validate(transfer)).isEmpty();
        assertThat(validator.validate(schedule)).isEmpty();
    }
}
