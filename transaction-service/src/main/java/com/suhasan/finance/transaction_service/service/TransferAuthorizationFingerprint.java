package com.suhasan.finance.transaction_service.service;

import com.suhasan.finance.transaction_service.dto.TransferRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class TransferAuthorizationFingerprint {
    private TransferAuthorizationFingerprint() {}

    public static String of(String userId, TransferRequest request) {
        String canonical = String.join("|",
                userId,
                value(request.getFromAccountId()),
                value(request.getToAccountId()),
                request.getAmount() == null ? "" : request.getAmount().stripTrailingZeros().toPlainString(),
                value(request.getCurrency()).toUpperCase(),
                value(request.getBeneficiaryId()),
                value(request.getDescription()),
                value(request.getReference()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint transfer", e);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
