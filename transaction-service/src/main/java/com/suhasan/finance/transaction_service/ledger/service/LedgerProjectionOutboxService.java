package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccount;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerBalanceProjection;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerProjectionOutbox;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerProjectionOutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class LedgerProjectionOutboxService {

    private final LedgerProjectionOutboxRepository outboxRepository;
    private final Clock clock;

    @Autowired
    public LedgerProjectionOutboxService(LedgerProjectionOutboxRepository outboxRepository) {
        this(outboxRepository, Clock.systemUTC());
    }

    LedgerProjectionOutboxService(LedgerProjectionOutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(LedgerAccount account, LedgerBalanceProjection projection, UUID sourceEventId) {
        if (account == null || projection == null || sourceEventId == null) {
            throw new IllegalArgumentException("Ledger account, projection, and source event id are required");
        }
        if (account.getAccountKind() != LedgerAccountKind.CUSTOMER) {
            return;
        }
        if (account.getExternalAccountId() == null || account.getExternalAccountId().isBlank()) {
            throw new IllegalArgumentException("Customer ledger account must have an external account id");
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        outboxRepository.save(LedgerProjectionOutbox.create(
                UUID.randomUUID(),
                account.getLedgerAccountId(),
                account.getExternalAccountId(),
                sourceEventId,
                projection.getProjectionVersion(),
                projection.getPostedBalance(),
                projection.getPendingBalance(),
                projection.getAvailableBalance(),
                account.getCurrency(),
                now
        ));
    }
}
