package com.suhasan.finance.transaction_service.ledger.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerOperationsRunbookTest {

    @Test
    void cutoverRunbookDocumentsPreconditionsEvidenceRollbackAndForwardFixBoundary() throws Exception {
        Path runbook = Path.of("..", "docs", "operations", "ledger-cutover-runbook.md").normalize();

        assertThat(runbook).exists();
        String content = Files.readString(runbook);
        assertThat(content).contains(
                "Maintenance preconditions",
                "Evidence capture",
                "Zero-critical-exception gate",
                "Rollback boundary",
                "After ledger authority is enabled",
                "forward fixes or compensating entries");
        assertThat(content).contains(
                "POST /api/admin/ledger/bootstrap",
                "POST /api/admin/reconciliation/runs",
                "ledger.authoritative=false",
                "ledger.authoritative=true");
    }
}
