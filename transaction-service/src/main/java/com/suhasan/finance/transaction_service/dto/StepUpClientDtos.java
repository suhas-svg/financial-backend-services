package com.suhasan.finance.transaction_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class StepUpClientDtos {
    private StepUpClientDtos() {}

    public record CreateChallengeRequest(String userId, String actionType, String actionFingerprint) {}
    public record CreateChallengeResponse(String challengeId, Instant expiresAt) {}
    public record ConsumeChallengeRequest(String userId, String actionFingerprint, String consumerKey, String proof) {}
    public record ConsumeChallengeResponse(boolean consumed, Instant consumedAt) {}
    public record AuthorizeTransferRequest(@NotBlank String proof) {}
}
