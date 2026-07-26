package com.suhasan.finance.transaction_service.client;

import com.suhasan.finance.transaction_service.dto.AccountDto;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class AccountServiceClientTest {
    private final AccountServiceClient client = spy(new AccountServiceClient(mock(WebClient.Builder.class)));

    @Test
    void validatesActiveInactiveMissingAndUnavailableAccounts() {
        AccountDto active = account(true, "CHECKING", BigDecimal.TEN, null);
        doReturn(active).when(client).getAccount("active");
        assertThat(client.validateAccount("active")).isTrue();

        doReturn(account(false, "CHECKING", BigDecimal.TEN, null)).when(client).getAccount("inactive");
        assertThat(client.validateAccount("inactive")).isFalse();
        doReturn(null).when(client).getAccount("missing");
        assertThat(client.validateAccount("missing")).isFalse();
        doThrow(new IllegalStateException("down")).when(client).getAccount("down");
        assertThat(client.validateAccount("down")).isFalse();
    }

    @Test
    void readsBalancesAndAppliesCreditAndDepositRules() {
        doReturn(account(true, "CHECKING", BigDecimal.TEN, null)).when(client).getAccount("checking");
        assertThat(client.getAccountBalance("checking")).isEqualByComparingTo("10");
        assertThat(client.hasSufficientBalance("checking", BigDecimal.ONE)).isTrue();
        assertThat(client.hasSufficientBalance("checking", BigDecimal.valueOf(11))).isFalse();

        doReturn(account(true, "CREDIT", BigDecimal.ZERO, BigDecimal.valueOf(20)))
                .when(client).getAccount("credit");
        assertThat(client.hasSufficientBalance("credit", BigDecimal.TEN)).isTrue();
        doReturn(account(true, "CREDIT", BigDecimal.ZERO, null)).when(client).getAccount("credit-null");
        assertThat(client.hasSufficientBalance("credit-null", BigDecimal.ONE)).isFalse();
        doReturn(null).when(client).getAccount("missing");
        assertThat(client.getAccountBalance("missing")).isZero();
        assertThat(client.hasSufficientBalance("missing", BigDecimal.ONE)).isFalse();
        doThrow(new IllegalStateException("down")).when(client).getAccount("down");
        assertThat(client.hasSufficientBalance("down", BigDecimal.ONE)).isFalse();
    }

    private AccountDto account(boolean active, String type, BigDecimal balance, BigDecimal credit) {
        AccountDto value = new AccountDto();
        value.setActive(active);
        value.setAccountType(type);
        value.setBalance(balance);
        value.setAvailableCredit(credit);
        return value;
    }
}
