package com.suhasan.finance.transaction_service.entity;

import java.util.List;

public enum RiskCaseStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    CLOSED;

    public static List<RiskCaseStatus> activeStatuses() {
        return List.of(OPEN, IN_REVIEW);
    }
}
