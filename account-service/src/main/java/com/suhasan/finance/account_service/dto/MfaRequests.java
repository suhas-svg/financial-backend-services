package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.NotBlank;

@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // Namespace for nested request records.
public final class MfaRequests {
    private MfaRequests() {}

    public record PasswordRequest(@NotBlank String currentPassword) {}
    public record ConfirmTotpRequest(@NotBlank String code) {}
    public record DisableTotpRequest(@NotBlank String currentPassword, @NotBlank String code) {}
    public record VerifyChallengeRequest(@NotBlank String credential) {}
}
