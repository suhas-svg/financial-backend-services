package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.ledger.domain.JournalState;

import java.util.UUID;

public record JournalResult(UUID journalId, JournalState state, boolean replay) {
}
