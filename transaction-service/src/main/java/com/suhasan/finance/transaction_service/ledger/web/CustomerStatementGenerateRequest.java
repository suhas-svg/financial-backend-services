package com.suhasan.finance.transaction_service.ledger.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerStatementGenerateRequest(
        @NotBlank(message = "externalAccountId must not be blank")
        String externalAccountId,

        @NotBlank(message = "yearMonth must not be blank")
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])",
                message = "yearMonth must be formatted as yyyy-MM, for example 2026-05")
        String yearMonth) {
}
