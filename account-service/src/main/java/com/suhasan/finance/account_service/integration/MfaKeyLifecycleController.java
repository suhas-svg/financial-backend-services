package com.suhasan.finance.account_service.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/integration-readiness/mfa-keys")
@RequiredArgsConstructor
public class MfaKeyLifecycleController {
    private final MfaKeyLifecycleService lifecycle;

    @GetMapping
    public Map<String, Object> health(Authentication authentication) {
        requireAdmin(authentication);
        return lifecycle.health();
    }

    @PostMapping("/rotate")
    public Map<String, Object> rotate(@RequestHeader("X-Operator-Request-Id") String requestId,
                                      Authentication authentication) {
        requireAdmin(authentication);
        return lifecycle.rotate(authentication.getName(), requestId);
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("ROLE_ADMIN is required");
        }
    }
}
