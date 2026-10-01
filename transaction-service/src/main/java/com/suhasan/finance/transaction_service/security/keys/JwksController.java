package com.suhasan.finance.transaction_service.security.keys;

import com.suhasan.finance.transaction_service.security.InternalServiceTokens;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Public keys that verify the internal service tokens this service signs (RFC 7517 JWK Set). */
@RestController
public class JwksController {

    private final InternalServiceTokens tokens;

    public JwksController(InternalServiceTokens tokens) {
        this.tokens = tokens;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(tokens.jwks());
    }
}
