package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.MfaRequests;
import com.suhasan.finance.account_service.dto.MfaResponses;
import com.suhasan.finance.account_service.security.ClientIpResolver;
import com.suhasan.finance.account_service.security.RefreshCookies;
import com.suhasan.finance.account_service.service.MfaService;
import com.suhasan.finance.account_service.service.PasswordChangeService;
import com.suhasan.finance.account_service.service.StepUpChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {
    private final MfaService mfaService;
    private final StepUpChallengeService challengeService;
    private final PasswordChangeService passwordChangeService;
    private final ClientIpResolver clientIpResolver;
    private final RefreshCookies refreshCookies;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.suhasan.finance.account_service.service.SpendingLimitService spendingLimitService;

    /**
     * Changes the password and signs the user out everywhere: every refresh session is revoked
     * and this browser's refresh cookie is cleared, so the next request signs in again.
     */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody final MfaRequests.ChangePasswordRequest request,
                                               final Authentication authentication,
                                               final HttpServletRequest httpRequest) {
        passwordChangeService.changePassword(authentication.getName(), request.currentPassword(),
                request.newPassword(), request.mfaCode(), clientIpResolver.resolve(httpRequest));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.clear()).build();
    }

    @GetMapping("/spending-limits")
    public java.util.List<com.suhasan.finance.account_service.dto.SpendingLimitDtos.LimitResponse> limits(final Authentication authentication) { return spendingLimitService.list(authentication.getName()); }

    @PutMapping("/spending-limits/{accountId}")
    public com.suhasan.finance.account_service.dto.SpendingLimitDtos.LimitResponse updateLimits(@PathVariable final Long accountId, @Valid @RequestBody final com.suhasan.finance.account_service.dto.SpendingLimitDtos.UpdateRequest request, final Authentication authentication) { return spendingLimitService.update(accountId, authentication.getName(), request); }

    @GetMapping("/mfa")
    public MfaResponses.StatusResponse status(final Authentication authentication) {
        return mfaService.status(authentication.getName());
    }

    @PostMapping("/mfa/totp/enroll")
    public MfaResponses.EnrollmentResponse enroll(@Valid @RequestBody final MfaRequests.PasswordRequest request,
                                                   final Authentication authentication) {
        return mfaService.enroll(authentication.getName(), request.currentPassword());
    }

    @PostMapping("/mfa/totp/confirm")
    public MfaResponses.ConfirmationResponse confirm(@Valid @RequestBody final MfaRequests.ConfirmTotpRequest request,
                                                      final Authentication authentication) {
        return mfaService.confirm(authentication.getName(), request.code());
    }

    @PostMapping("/mfa/recovery-codes/regenerate")
    public MfaResponses.RecoveryCodesResponse regenerate(@Valid @RequestBody final MfaRequests.PasswordRequest request,
                                                          final Authentication authentication) {
        return mfaService.regenerateRecoveryCodes(authentication.getName(), request.currentPassword());
    }

    @DeleteMapping("/mfa/totp")
    public ResponseEntity<Void> disable(@Valid @RequestBody final MfaRequests.DisableTotpRequest request,
                                        final Authentication authentication) {
        mfaService.disable(authentication.getName(), request.currentPassword(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/challenges/{challengeId}/verify")
    public MfaResponses.ChallengeVerificationResponse verify(@PathVariable final String challengeId,
                                                              @Valid @RequestBody final MfaRequests.VerifyChallengeRequest request,
                                                              final Authentication authentication) {
        return challengeService.verify(challengeId, authentication.getName(), request.credential());
    }
}
