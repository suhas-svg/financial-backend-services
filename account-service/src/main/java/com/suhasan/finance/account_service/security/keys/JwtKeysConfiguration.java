package com.suhasan.finance.account_service.security.keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Asymmetric token keys. account-service signs user access tokens with its own RSA key and
 * publishes the public half at {@code /.well-known/jwks.json}; transaction-service verifies them
 * from there. Internal service tokens go the other way: transaction-service signs them and this
 * service verifies them against transaction-service's JWKS. No secret is shared between services.
 */
@Configuration
public class JwtKeysConfiguration {

    @Bean
    public RsaSigningKeys userTokenSigningKeys(
            @Value("${security.jwt.signing.private-key:}") final String privateKey,
            @Value("${security.jwt.signing.key-id:}") final String keyId,
            @Value("${security.jwt.signing.previous-public-key:}") final String previousPublicKey,
            @Value("${security.jwt.signing.previous-key-id:}") final String previousKeyId,
            @Value("${security.jwt.signing.allow-generated-key:true}") final boolean allowGeneratedKey) {
        return new RsaSigningKeys("user access token", privateKey, keyId, previousPublicKey, previousKeyId,
                allowGeneratedKey);
    }

    @Bean
    public RemoteJwks internalTokenVerificationKeys(
            @Value("${security.jwt.internal.jwks-uri:}") final String jwksUri,
            @Value("${security.jwt.internal.public-key:}") final String publicKey) {
        return new RemoteJwks(jwksUri, publicKey);
    }
}
