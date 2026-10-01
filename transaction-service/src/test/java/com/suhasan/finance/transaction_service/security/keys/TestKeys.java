package com.suhasan.finance.transaction_service.security.keys;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * RSA key pairs generated once per test JVM. Keys are never committed: the full-history
 * secret scan would rightly flag a private key in the repository.
 */
public final class TestKeys {

    public static final KeyPair USER = generate();
    public static final KeyPair INTERNAL = generate();
    public static final KeyPair OTHER = generate();

    private TestKeys() {
    }

    /** transaction-service's internal-token signer, built from {@link #INTERNAL}. */
    public static RsaSigningKeys internalSigningKeys() {
        return new RsaSigningKeys("internal service token", privatePem(INTERNAL.getPrivate()),
                "internal-test-key", null, null, false);
    }

    public static String privatePem(final PrivateKey key) {
        return pem("PRIVATE KEY", key.getEncoded());
    }

    public static String publicPem(final PublicKey key) {
        return pem("PUBLIC KEY", key.getEncoded());
    }

    private static String pem(final String type, final byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    private static KeyPair generate() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
