package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.repository.JournalTransactionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerBootstrapPreflightServiceTest {
    @Mock LedgerBootstrapAccountSource accountSource;
    @Mock LedgerBootstrapService bootstrapService;
    @Mock LedgerAccountRepository ledgerAccountRepository;
    @Mock JournalTransactionRepository journalTransactionRepository;
    @Mock TransactionRepository transactionRepository;
    LedgerBootstrapPreflightService service;

    @BeforeEach
    void setUp() {
        service = new LedgerBootstrapPreflightService(accountSource, bootstrapService, ledgerAccountRepository,
                journalTransactionRepository, transactionRepository);
        when(accountSource.fetchAccountsForBootstrap()).thenReturn(List.of());
        when(bootstrapService.requiredSystemCurrencies()).thenReturn(List.of("USD", "EUR", "GBP", "INR"));
        when(ledgerAccountRepository.findByAccountKindAndCurrency(any(LedgerAccountKind.class), any()))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING)).thenReturn(List.of());
        when(transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PROCESSING)).thenReturn(List.of());
    }

    @Test
    void reportsFreshDatabaseAndAllMissingInrSystemAccountsWithoutWriting() {
        LedgerBootstrapPreflight result = service.inspect(true);

        assertThat(result.ready()).isTrue();
        assertThat(result.freshDatabase()).isTrue();
        assertThat(result.requiredCurrencies()).containsExactly("USD", "EUR", "GBP", "INR");
        assertThat(result.missingSystemAccounts()).hasSize(12)
                .contains("CLEARING:INR", "SUSPENSE:INR", "FEE:INR");
        assertThat(result.blockers()).isEmpty();
    }

    @Test
    void systemAccountsMakeDatabaseNonFreshEvenWithoutCustomerFunds() {
        when(ledgerAccountRepository.count()).thenReturn(12L);

        LedgerBootstrapPreflight result = service.inspect(true);

        assertThat(result.freshDatabase()).isFalse();
        assertThat(result.ledgerAccountCount()).isEqualTo(12);
    }
    @Test
    void failsClosedWithoutExplicitMaintenanceConfirmation() {
        LedgerBootstrapPreflight result = service.inspect(false);

        assertThat(result.ready()).isFalse();
        assertThat(result.blockers()).containsExactly("Maintenance mode must be explicitly confirmed");
    }
}
