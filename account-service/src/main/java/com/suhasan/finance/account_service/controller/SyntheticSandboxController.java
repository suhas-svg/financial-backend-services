package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.sandbox.SyntheticSandboxBootstrapService;
import com.suhasan.finance.account_service.sandbox.SyntheticSandboxGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
@RequiredArgsConstructor
public class SyntheticSandboxController {
    private final SyntheticSandboxGuard guard;
    private final SyntheticSandboxBootstrapService bootstrapService;

    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        return Map.of("environment", guard.isSynthetic() ? "SYNTHETIC_SANDBOX" : "NON_SYNTHETIC",
                "synthetic", guard.isSynthetic(), "realMoney", false,
                "runtimeScope", "frontend,gateway,account-service,transaction-service,postgres,redis");
    }

    @GetMapping("/bootstrap/status")
    public SyntheticSandboxBootstrapService.BootstrapStatus status() {
        return bootstrapService.status();
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<SyntheticSandboxBootstrapService.BootstrapStatus> bootstrap(
            @RequestHeader("X-Sandbox-Bootstrap-Token") final String token,
            @Valid @RequestBody final BootstrapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bootstrapService.bootstrap(token, request.username(), request.password()));
    }

    public record BootstrapRequest(@NotBlank @Size(min = 5, max = 64) String username,
                                   @NotBlank @Size(min = 14, max = 200) String password) {}
}
