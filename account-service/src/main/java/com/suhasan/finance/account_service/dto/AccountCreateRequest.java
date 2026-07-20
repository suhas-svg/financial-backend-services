package com.suhasan.finance.account_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AccountCreateRequest(
        @NotBlank String accountType,
        String ownerId,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @PositiveOrZero Double interestRate,
        @PositiveOrZero BigDecimal creditLimit,
        @Future LocalDate dueDate) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unsupported account field: " + field);
    }
}
