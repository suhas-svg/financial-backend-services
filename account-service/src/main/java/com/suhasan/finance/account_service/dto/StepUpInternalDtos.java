package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class StepUpInternalDtos {
    private StepUpInternalDtos() {}

    public record CreateChallengeRequest(
            @NotBlank String userId,
            @NotBlank String actionType,
            @NotBlank String actionFingerprint) {}

    public record CreateChallengeResponse(String challengeId, Instant expiresAt) {}

    public record ConsumeChallengeRequest(
            @NotBlank String userId,
            @NotBlank String actionFingerprint,
            @NotBlank String consumerKey,
            @NotBlank String proof) {}

    public record ConsumeChallengeResponse(boolean consumed, Instant consumedAt) {}
}
