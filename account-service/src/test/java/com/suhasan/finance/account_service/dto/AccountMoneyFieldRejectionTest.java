package com.suhasan.finance.account_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountMoneyFieldRejectionTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createContractRejectsCallerControlledBalance() {
        assertThatThrownBy(() -> objectMapper.readValue(
                """
                {"accountType":"CHECKING","ownerId":"customer","currency":"USD","balance":999}
                """, AccountCreateRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported account field: balance");
    }

    @Test
    void metadataContractRejectsLedgerAndAvailableBalances() {
        assertThatThrownBy(() -> objectMapper.readValue(
                """
                {"ledgerBalance":999,"availableBalance":999}
                """, AccountMetadataUpdateRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported account field");
    }
}
