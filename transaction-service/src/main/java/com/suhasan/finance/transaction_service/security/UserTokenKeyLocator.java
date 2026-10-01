package com.suhasan.finance.transaction_service.security;

import com.suhasan.finance.transaction_service.security.keys.RemoteJwks;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.UnsupportedJwtException;

import java.security.Key;

/**
 * Picks the account-service public key for a user access token by its {@code kid}. Only RS256
 * is accepted, so a published public key can never be replayed as an HMAC secret.
 */
public final class UserTokenKeyLocator extends LocatorAdapter<Key> {

    private final RemoteJwks keys;

    public UserTokenKeyLocator(RemoteJwks keys) {
        this.keys = keys;
    }

    @Override
    protected Key locate(JwsHeader header) {
        if (!"RS256".equals(header.getAlgorithm())) {
            throw new UnsupportedJwtException("Only RS256 tokens are accepted");
        }
        return keys.resolve(header.getKeyId())
                .orElseThrow(() -> new UnsupportedJwtException("Unknown signing key"));
    }
}
