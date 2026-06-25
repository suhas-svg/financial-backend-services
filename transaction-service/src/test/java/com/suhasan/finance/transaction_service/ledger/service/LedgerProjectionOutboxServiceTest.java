package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountStatus;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerProjectionOutbox;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerProjectionOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LedgerProjectionOutboxServiceTest {

    @Mock private LedgerProjectionOutboxRepository outboxRepository;

    private Clock clock;
    private LedgerProjectionOutboxService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-25T04:00:00Z"), ZoneOffset.UTC);
        service = new LedgerProjectionOutboxService(outboxRepository, clock);
    }

    @Test
    void enqueueCopiesCustomerProjectionSnapshotWithUniqueSourceEventId() {
        UUID ledgerAccountId = UUID.randomUUID();
        LedgerAccount account = LedgerAccount.builder()
                .ledgerAccountId(ledgerAccountId)
                .accountKind(LedgerAccountKind.CUSTOMER)
                .currency("USD")
                .externalAccountId("101")
                .status(LedgerAccountStatus.ACTIVE)
                .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .build();
        LedgerBalanceProjection projection = LedgerBalanceProjection.open(
                ledgerAccountId, new BigDecimal("100.00"));
        projection.reserveDebit(new BigDecimal("25.00"), 1L);

        service.enqueue(account, projection, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        ArgumentCaptor<LedgerProjectionOutbox> captor = ArgumentCaptor.forClass(LedgerProjectionOutbox.class);
        verify(outboxRepository).save(captor.capture());
        LedgerProjectionOutbox message = captor.getValue();
        assertThat(message.getLedgerAccountId()).isEqualTo(ledgerAccountId);
        assertThat(message.getExternalAccountId()).isEqualTo("101");
        assertThat(message.getSourceEventId()).isEqualTo(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertThat(message.getProjectionVersion()).isEqualTo(projection.getProjectionVersion());
        assertThat(message.getPostedBalance()).isEqualByComparingTo("100.00");
        assertThat(message.getPendingBalance()).isEqualByComparingTo("-25.00");
        assertThat(message.getAvailableBalance()).isEqualByComparingTo("75.00");
        assertThat(message.getCurrency()).isEqualTo("USD");
        assertThat(message.getCreatedAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
