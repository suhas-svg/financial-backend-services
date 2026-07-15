package com.suhasan.finance.transaction_service.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ledger_bootstrap_runs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LedgerBootstrapRun {
    @Id @Column(name = "run_id", length = 36)
    private String runId;
    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;
    @Column(nullable = false, length = 32)
    private String mode;
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;
    @Column(name = "maintenance_mode", nullable = false)
    private boolean maintenanceMode;
    @Column(nullable = false, length = 24)
    private String outcome;
    @Column(name = "imported_accounts")
    private Integer importedAccounts;
    @Column(name = "reused_accounts")
    private Integer reusedAccounts;
    @Column(name = "seeded_system_accounts")
    private Integer seededSystemAccounts;
    @Column(name = "opening_journals")
    private Integer openingJournals;
    @Column(name = "currencies_json", columnDefinition = "TEXT")
    private String currenciesJson;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
}
