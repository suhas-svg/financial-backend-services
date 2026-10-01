package com.suhasan.finance.account_service.security.keys;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads PEM-encoded RSA keys and renders RSA public keys as JWKs (RFC 7517). */
public final class PemKeys {

    private static final String RSA = "RSA";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private PemKeys() {
    }

    /** PKCS#8 "BEGIN PRIVATE KEY" PEM, e.g. from {@code openssl genpkey -algorithm RSA}. */
    public static RSAPrivateCrtKey privateKey(final String pem) {
        try {
            final PrivateKey key = KeyFactory.getInstance(RSA)
                    .generatePrivate(new PKCS8EncodedKeySpec(decode(pem, "PRIVATE KEY")));
            if (!(key instanceof RSAPrivateCrtKey rsa)) {
                throw new IllegalArgumentException("Signing key must be an RSA private key with CRT parameters");
            }
            return rsa;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Signing key is not a valid PKCS#8 RSA private key", e);
        }
    }

    /** X.509 "BEGIN PUBLIC KEY" PEM. */
    public static RSAPublicKey publicKey(final String pem) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance(RSA)
                    .generatePublic(new X509EncodedKeySpec(decode(pem, "PUBLIC KEY")));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalArgumentException("Verification key is not a valid X.509 RSA public key", e);
        }
    }

    public static RSAPublicKey publicKeyOf(final RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance(RSA).generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive the RSA public key", e);
        }
    }

    public static RSAPublicKey fromJwk(final String modulus, final String exponent) {
        try {
            final Base64.Decoder decoder = Base64.getUrlDecoder();
            return (RSAPublicKey) KeyFactory.getInstance(RSA).generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, decoder.decode(modulus)), new BigInteger(1, decoder.decode(exponent))));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("JWK is not a valid RSA public key", e);
        }
    }

    public static Map<String, Object> toJwk(final String keyId, final RSAPublicKey key) {
        final Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", RSA);
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", URL_ENCODER.encodeToString(unsigned(key.getModulus())));
        jwk.put("e", URL_ENCODER.encodeToString(unsigned(key.getPublicExponent())));
        return jwk;
    }

    private static byte[] unsigned(final BigInteger value) {
        final byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            final byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static byte[] decode(final String pem, final String type) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Key PEM is empty");
        }
        // Accept real newlines or "\n" escapes (keys passed through single-line env vars).
        final String body = pem.replace("\\n", "\n")
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Key PEM is not valid base64 (" + type + ")", e);
        }
    }
}
