package com.suhasan.finance.account_service.security.keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An RS256 signing key plus the public keys this service publishes in its JWKS.
 *
 * <p>The private key comes from a secret (PKCS#8 PEM). During a rotation the previous key's
 * public half stays published until every token it signed has expired. Without a configured
 * key a fresh one can be generated at startup, which only works with a single replica and
 * restarts invalidate outstanding tokens; production refuses to start that way.
 */
public final class RsaSigningKeys {

    private static final Logger LOG = LoggerFactory.getLogger(RsaSigningKeys.class);
    private static final int GENERATED_KEY_BITS = 2048;

    private final String currentKeyId;
    private final RSAPrivateCrtKey signingKey;
    private final Map<String, RSAPublicKey> published = new LinkedHashMap<>();

    public RsaSigningKeys(final String purpose, final String privateKeyPem, final String configuredKeyId,
                          final String previousPublicKeyPem, final String previousKeyId,
                          final boolean allowGeneratedKey) {
        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            this.signingKey = PemKeys.privateKey(privateKeyPem);
        } else if (allowGeneratedKey) {
            this.signingKey = generate();
            if (LOG.isWarnEnabled()) {
                LOG.warn("No {} signing key configured; generated a temporary key. Tokens stop verifying when "
                        + "this process restarts, and replicas cannot share it. Configure one for any shared "
                        + "environment.", purpose);
            }
        } else {
            throw new IllegalStateException("A " + purpose + " signing key is required (PKCS#8 RSA private key PEM)");
        }
        final RSAPublicKey publicKey = PemKeys.publicKeyOf(signingKey);
        this.currentKeyId = configuredKeyId == null || configuredKeyId.isBlank() ? thumbprint(publicKey) : configuredKeyId;
        published.put(currentKeyId, publicKey);
        if (previousPublicKeyPem != null && !previousPublicKeyPem.isBlank()) {
            final RSAPublicKey previous = PemKeys.publicKey(previousPublicKeyPem);
            final String previousId = previousKeyId == null || previousKeyId.isBlank()
                    ? thumbprint(previous) : previousKeyId;
            if (previousId.equals(currentKeyId)) {
                throw new IllegalStateException("The previous " + purpose + " key id must differ from the current one");
            }
            published.put(previousId, previous);
        }
    }

    public String keyId() {
        return currentKeyId;
    }

    public RSAPrivateCrtKey privateKey() {
        return signingKey;
    }

    public Optional<RSAPublicKey> publicKey(final String id) {
        return Optional.ofNullable(published.get(id));
    }

    /** The JWK Set document served at {@code /.well-known/jwks.json}. */
    public Map<String, Object> jwks() {
        final List<Map<String, Object>> keys = new ArrayList<>();
        published.forEach((id, key) -> keys.add(PemKeys.toJwk(id, key)));
        return Map.of("keys", keys);
    }

    private static RSAPrivateCrtKey generate() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(GENERATED_KEY_BITS);
            return (RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }

    /** A stable id derived from the public key (RFC 7638 style), so rotations get new ids. */
    static String thumbprint(final RSAPublicKey key) {
        final Map<String, Object> jwk = PemKeys.toJwk("", key);
        final String canonical = "{\"e\":\"" + jwk.get("e") + "\",\"kty\":\"RSA\",\"n\":\"" + jwk.get("n") + "\"}";
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
