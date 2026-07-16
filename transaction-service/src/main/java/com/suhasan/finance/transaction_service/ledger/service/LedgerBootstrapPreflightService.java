package com.suhasan.finance.transaction_service.ledger.service;

import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.ledger.domain.LedgerAccountKind;
import com.suhasan.finance.transaction_service.ledger.repository.JournalTransactionRepository;
import com.suhasan.finance.transaction_service.ledger.repository.LedgerAccountRepository;
import com.suhasan.finance.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerBootstrapPreflightService {
    private final LedgerBootstrapAccountSource accountSource;
    private final LedgerBootstrapService bootstrapService;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final JournalTransactionRepository journalTransactionRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public LedgerBootstrapPreflight inspect(boolean maintenanceModeConfirmed) {
        return inspect(maintenanceModeConfirmed, "system-startup", "SYSTEM_STARTUP", "startup-preflight", true);
    }

    @Transactional(readOnly = true)
    public LedgerBootstrapPreflight inspect(boolean maintenanceModeConfirmed, String operatorId, String operatorRole,
                                            String requestId, boolean operatorAuthorized) {
        List<LedgerBootstrapAccountSnapshot> legacyAccounts = accountSource.fetchAccountsForBootstrap();
        List<String> blockers = new ArrayList<>();
        if (!operatorAuthorized) {
            blockers.add("Explicit ROLE_ADMIN operator authorization is required");
        }
        if (requestId == null || requestId.isBlank()) {
            blockers.add("Operator request ID is required");
        }
        if (!maintenanceModeConfirmed) {
            blockers.add("Maintenance mode must be explicitly confirmed");
        }
        long unresolvedHolds = legacyAccounts.stream()
                .filter(LedgerBootstrapAccountSnapshot::hasUnresolvedLegacyHold).count();
        if (unresolvedHolds > 0) {
            blockers.add(unresolvedHolds + " unresolved legacy holds");
        }
        long pending = transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PENDING).size();
        long processing = transactionRepository.findByStatusOrderByCreatedAtDesc(TransactionStatus.PROCESSING).size();
        if (pending + processing > 0) {
            blockers.add((pending + processing) + " processing transactions");
        }

        List<String> requiredCurrencies = bootstrapService.requiredSystemCurrencies();
        List<String> missingSystemAccounts = new ArrayList<>();
        for (String currency : requiredCurrencies) {
            for (LedgerAccountKind kind : List.of(LedgerAccountKind.CLEARING, LedgerAccountKind.SUSPENSE, LedgerAccountKind.FEE)) {
                if (ledgerAccountRepository.findByAccountKindAndCurrency(kind, currency).isEmpty()) {
                    missingSystemAccounts.add(kind + ":" + currency);
                }
            }
        }
        long ledgerAccounts = ledgerAccountRepository.count();
        long customerLedgerAccounts = ledgerAccountRepository.countByAccountKind(LedgerAccountKind.CUSTOMER);
        long journalCount = journalTransactionRepository.count();
        long transactionCount = transactionRepository.count();
        boolean freshDatabase = legacyAccounts.isEmpty() && ledgerAccounts == 0
                && journalCount == 0 && transactionCount == 0;
        return new LedgerBootstrapPreflight(maintenanceModeConfirmed, blockers.isEmpty(), freshDatabase,
                requiredCurrencies, List.copyOf(missingSystemAccounts), legacyAccounts.size(),
                ledgerAccounts, customerLedgerAccounts, journalCount, transactionCount, List.copyOf(blockers),
                operatorId, operatorRole, operatorAuthorized, requestId);
    }
}
