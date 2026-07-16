package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
    public LedgerBootstrapPreflight preflight(
            @RequestParam(defaultValue = "false") boolean maintenanceMode,
            @RequestHeader(value = "X-Operator-Request-Id", required = false) String requestId,
            Authentication authentication) {
        requireAdmin(authentication);
        return preflightService.inspect(maintenanceMode, authentication.getName(), "ROLE_ADMIN",
                requireRequestId(requestId), true);
    }

    @PostMapping("/bootstrap")
    public LedgerBootstrapResult bootstrap(
            @RequestBody LedgerBootstrapRequest request,
            @RequestHeader(value = "X-Operator-Request-Id", required = false) String requestId,
            Authentication authentication) {
        requireAdmin(authentication);
        LocalDate businessDate = request.businessDate() == null ? LocalDate.now() : request.businessDate();
        return bootstrapCoordinator.bootstrap(new LedgerBootstrapCommand(
                authentication.getName(), "ROLE_ADMIN", requireRequestId(requestId),
                request.enabled(), request.maintenanceMode(), businessDate), "ADMIN_API");
    }

    private void requireAdmin(Authentication authentication) {
        boolean admin = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) throw new AccessDeniedException("Ledger bootstrap requires an explicit ROLE_ADMIN operator");
    }

    private String requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw new IllegalArgumentException("X-Operator-Request-Id must contain 1 to 128 characters");
        }
        return requestId.trim();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIllegalState(IllegalStateException exception) {
        return Map.of("message", exception.getMessage());
    }
}
