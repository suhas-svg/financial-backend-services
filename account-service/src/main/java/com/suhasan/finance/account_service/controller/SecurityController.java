package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.MfaRequests;
import com.suhasan.finance.account_service.dto.MfaResponses;
import com.suhasan.finance.account_service.service.MfaService;
import com.suhasan.finance.account_service.service.StepUpChallengeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {
    private final MfaService mfaService;
    private final StepUpChallengeService challengeService;

    @GetMapping("/mfa")
    public MfaResponses.StatusResponse status(Authentication authentication) {
        return mfaService.status(authentication.getName());
    }

    @PostMapping("/mfa/totp/enroll")
    public MfaResponses.EnrollmentResponse enroll(@Valid @RequestBody MfaRequests.PasswordRequest request,
                                                   Authentication authentication) {
        return mfaService.enroll(authentication.getName(), request.currentPassword());
    }

    @PostMapping("/mfa/totp/confirm")
    public MfaResponses.ConfirmationResponse confirm(@Valid @RequestBody MfaRequests.ConfirmTotpRequest request,
                                                      Authentication authentication) {
        return mfaService.confirm(authentication.getName(), request.code());
    }

    @PostMapping("/mfa/recovery-codes/regenerate")
    public MfaResponses.RecoveryCodesResponse regenerate(@Valid @RequestBody MfaRequests.PasswordRequest request,
                                                          Authentication authentication) {
        return mfaService.regenerateRecoveryCodes(authentication.getName(), request.currentPassword());
    }

    @DeleteMapping("/mfa/totp")
    public ResponseEntity<Void> disable(@Valid @RequestBody MfaRequests.DisableTotpRequest request,
                                        Authentication authentication) {
        mfaService.disable(authentication.getName(), request.currentPassword(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/challenges/{challengeId}/verify")
    public MfaResponses.ChallengeVerificationResponse verify(@PathVariable String challengeId,
                                                              @Valid @RequestBody MfaRequests.VerifyChallengeRequest request,
                                                              Authentication authentication) {
        return challengeService.verify(challengeId, authentication.getName(), request.credential());
    }
}
