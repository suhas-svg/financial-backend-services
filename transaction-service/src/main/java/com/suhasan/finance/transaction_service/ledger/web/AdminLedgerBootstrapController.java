package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapCommand;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapResult;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ledger")
public class AdminLedgerBootstrapController {

    private final LedgerBootstrapService bootstrapService;

    public AdminLedgerBootstrapController(LedgerBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @PostMapping("/bootstrap")
    public LedgerBootstrapResult bootstrap(
            @RequestBody LedgerBootstrapRequest request,
            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "unknown";
        LocalDate businessDate = request.businessDate() == null ? LocalDate.now() : request.businessDate();
        return bootstrapService.bootstrap(new LedgerBootstrapCommand(
                actor,
                request.enabled(),
                request.maintenanceMode(),
                businessDate));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIllegalState(IllegalStateException exception) {
        return Map.of("message", exception.getMessage());
    }
}
