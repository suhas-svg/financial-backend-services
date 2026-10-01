package com.suhasan.finance.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // Namespace for nested request records.
public final class MfaRequests {
    private MfaRequests() {}

    public record PasswordRequest(@NotBlank String currentPassword) {}
    public record ConfirmTotpRequest(@NotBlank String code) {}
    public record DisableTotpRequest(@NotBlank String currentPassword, @NotBlank String code) {}
    public record VerifyChallengeRequest(@NotBlank String credential) {}
    /** mfaCode is required when the user has an authenticator enrolled. */
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 12, max = 128, message = "Password must be 12 to 128 characters") String newPassword,
                                        String mfaCode) {}
}
