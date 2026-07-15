package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapCommand;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapCoordinator;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapPreflight;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapPreflightService;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ledger")
public class AdminLedgerBootstrapController {

    private final LedgerBootstrapCoordinator bootstrapCoordinator;
    private final LedgerBootstrapPreflightService preflightService;

    public AdminLedgerBootstrapController(LedgerBootstrapCoordinator bootstrapCoordinator,
                                          LedgerBootstrapPreflightService preflightService) {
        this.bootstrapCoordinator = bootstrapCoordinator;
        this.preflightService = preflightService;
    }

    @GetMapping("/bootstrap/preflight")
    public LedgerBootstrapPreflight preflight(@RequestParam(defaultValue = "false") boolean maintenanceMode) {
        return preflightService.inspect(maintenanceMode);
    }

    @PostMapping("/bootstrap")
    public LedgerBootstrapResult bootstrap(
            @RequestBody LedgerBootstrapRequest request,
            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";
        LocalDate businessDate = request.businessDate() == null ? LocalDate.now() : request.businessDate();
        return bootstrapCoordinator.bootstrap(new LedgerBootstrapCommand(
                actor,
                request.enabled(),
                request.maintenanceMode(),
                businessDate), "ADMIN_API");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIllegalState(IllegalStateException exception) {
        return Map.of("message", exception.getMessage());
    }
}
