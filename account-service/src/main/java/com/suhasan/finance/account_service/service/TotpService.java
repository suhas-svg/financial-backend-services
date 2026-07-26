package com.suhasan.finance.account_service.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;

@Service
@SuppressWarnings("PMD.AvoidLiteralsInIfCondition") // Base32 decoding operates on fixed bit-width boundaries.
public class TotpService {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        final byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String provisioningUri(final String username, final String secret) {
        final String label = url("Financial Backend:" + username);
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + url("Financial Backend") + "&algorithm=SHA1&digits=6&period=30";
    }

    public boolean verify(final String secret, final String code, final Instant now) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        final long counter = now.getEpochSecond() / 30;
        for (long offset = -1; offset <= 1; offset++) {
            if (constantTimeEquals(code, generateCode(secret, counter + offset))) {
                return true;
            }
        }
        return false;
    }

    String generateCode(final String secret, final long counter) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            final byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            final int offset = hash[hash.length - 1] & 0x0f;
            final int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Unable to generate TOTP", e);
        }
    }

    private boolean constantTimeEquals(final String left, final String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private String encodeBase32(final byte[] data) {
        final StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (final byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32[(buffer >> (bitsLeft - 5)) & 31]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32[(buffer << (5 - bitsLeft)) & 31]);
        }
        return result.toString();
    }

    private byte[] decodeBase32(final String value) {
        final String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        final ByteBuffer output = ByteBuffer.allocate((normalized.length() * 5 + 7) / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (final char c : normalized.toCharArray()) {
            final int index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.put((byte) ((buffer >> (bitsLeft - 8)) & 0xff));
                bitsLeft -= 8;
            }
        }
        final byte[] bytes = new byte[output.position()];
        output.flip();
        output.get(bytes);
        return bytes;
    }

    private String url(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
