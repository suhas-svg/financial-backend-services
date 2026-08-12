package com.suhasan.finance.transaction_service.outcome.service;

public class ScenarioDivergedException extends IllegalStateException {
    public static final String CODE = "SCENARIO_DIVERGED";
    public static final String RECOVERY = "Authoritative state changed. Refresh or re-run the scenario and select and consent to a fresh repair.";

    public ScenarioDivergedException() {
        super(CODE + ": " + RECOVERY);
    }
}
