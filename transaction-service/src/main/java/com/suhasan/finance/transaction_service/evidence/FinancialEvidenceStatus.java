package com.suhasan.finance.transaction_service.evidence;

public enum FinancialEvidenceStatus {
    PENDING,
    RETRY_SCHEDULED,
    DELIVERED,
    TERMINAL_FAILED
}
