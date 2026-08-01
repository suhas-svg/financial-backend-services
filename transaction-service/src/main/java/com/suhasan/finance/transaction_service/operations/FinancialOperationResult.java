package com.suhasan.finance.transaction_service.operations;

import java.time.LocalDate;

public record FinancialOperationResult(
        String operationType,
        LocalDate businessDate,
        String status,
        boolean executed,
        int processedItems,
        String evidence) {
}
