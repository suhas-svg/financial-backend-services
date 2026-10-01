package com.suhasan.finance.account_service.security.keys;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Public keys that verify the user access tokens this service signs (RFC 7517 JWK Set). */
@RestController
public class JwksController {

    private final RsaSigningKeys signingKeys;

    public JwksController(final RsaSigningKeys signingKeys) {
        this.signingKeys = signingKeys;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(signingKeys.jwks());
    }
}
