package com.suhasan.finance.transaction_service.ledger.web;

import java.util.UUID;

public record ReconciliationExceptionNoteResponse(
        UUID noteId,
        String author,
        String note) {
}
