package com.suhasan.finance.transaction_service.entity;

import java.util.List;

public enum DisputeStatus {
    OPEN,
    IN_REVIEW,
    APPROVED,
    DENIED,
    CLOSED;

    public static List<DisputeStatus> activeStatuses() {
        return List.of(OPEN, IN_REVIEW);
    }

    public boolean isClosedStatus() {
        return this == APPROVED || this == DENIED || this == CLOSED;
    }
}
