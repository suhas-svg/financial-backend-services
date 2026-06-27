package com.suhasan.finance.transaction_service.ledger.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class JournalRequestFingerprint {

    private JournalRequestFingerprint() {
    }

    public static String forReversal(UUID originalJournalId, String reason) {
        return sha256(originalJournalId + "\n" + reason);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
