package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.StepUpClientDtos;
import com.suhasan.finance.transaction_service.sandbox.SyntheticSandboxGuard;
import com.suhasan.finance.transaction_service.sandbox.SyntheticSandboxSeedService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
@RequiredArgsConstructor
public class SyntheticSandboxSeedController {
    private final SyntheticSandboxGuard guard;
    private final SyntheticSandboxSeedService seedService;

    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        return Map.of("environment", guard.isSynthetic() ? "SYNTHETIC_SANDBOX" : "NON_SYNTHETIC",
                "synthetic", guard.isSynthetic(), "realMoney", false, "seedVersion", "controlled-beta-phase2-v1");
    }

    @PostMapping("/seed/challenge")
    public StepUpClientDtos.CreateChallengeResponse challenge(
            @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication authentication) {
        requireAdmin(authentication);
        return seedService.challenge(authentication.getName(), idempotencyKey);
    }

    @PostMapping("/seed")
    public ResponseEntity<SyntheticSandboxSeedService.SeedResult> seed(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SeedRequest request, Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(seedService.seed(authentication.getName(),
                idempotencyKey, request.challengeId(), request.proof()));
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Synthetic sandbox seeding requires an operator");
        }
    }

    public record SeedRequest(@NotBlank String challengeId, @NotBlank String proof) {}
}
