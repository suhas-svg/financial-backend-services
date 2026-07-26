package com.suhasan.finance.account_service.dto;

import java.time.Instant;
import java.util.List;

@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // Namespace for nested response records.
public final class MfaResponses {
    private MfaResponses() {}

    public record StatusResponse(boolean enrolled, String status, long recoveryCodesRemaining) {}
    public record EnrollmentResponse(String secret, String otpauthUri) {}
    public record ConfirmationResponse(boolean active, List<String> recoveryCodes) {
        public ConfirmationResponse {
            recoveryCodes = List.copyOf(recoveryCodes);
        }
    }
    public record RecoveryCodesResponse(List<String> recoveryCodes) {
        public RecoveryCodesResponse {
            recoveryCodes = List.copyOf(recoveryCodes);
        }
    }
    public record ChallengeVerificationResponse(String challengeId, String proof, Instant proofExpiresAt) {}
}
