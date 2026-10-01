package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.MfaResponses;
import com.suhasan.finance.account_service.entity.MfaMethod;
import com.suhasan.finance.account_service.entity.MfaMethodStatus;
import com.suhasan.finance.account_service.entity.MfaRecoveryCode;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.exception.MfaVerificationException;
import com.suhasan.finance.account_service.integration.MfaSecretManager;
import com.suhasan.finance.account_service.repository.MfaMethodRepository;
import com.suhasan.finance.account_service.repository.MfaRecoveryCodeRepository;
import com.suhasan.finance.account_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@SuppressWarnings({
        "PMD.AvoidInstantiatingObjectsInLoops", // Every recovery code requires its own persisted entity.
        "PMD.AvoidLiteralsInIfCondition" // Recovery-code grouping position is part of the display format.
})
public class MfaService {
    private static final String METHOD = "TOTP";
    private static final int RECOVERY_CODE_COUNT = 8;

    /**
     * Consecutive failed TOTP verifications tolerated before the code space is refused outright.
     * Bounds online guessing against a six-digit code to a few hundred attempts per lockout.
     */
    static final int MAX_VERIFICATION_ATTEMPTS = 5;

    /** How long a locked method refuses codes before the attempt counter is cleared. */
    static final Duration VERIFICATION_LOCKOUT = Duration.ofMinutes(15);

    /** Returned for every verification failure so callers cannot distinguish a wrong code from a lockout. */
    private static final String INVALID_CODE_MESSAGE = "Invalid authentication code";

    private final MfaMethodRepository methodRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaSecretManager secretManager;
    private final TotpService totpService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public MfaResponses.StatusResponse status(final String username) {
        return methodRepository.findByUserIdAndMethodType(username, METHOD)
                .map(method -> new MfaResponses.StatusResponse(
                        method.getStatus() == MfaMethodStatus.ACTIVE,
                        method.getStatus().name(),
                        recoveryCodeRepository.countByMfaMethodIdAndUsedAtIsNull(method.getId())))
                .orElseGet(() -> new MfaResponses.StatusResponse(false, "NOT_ENROLLED", 0));
    }

    public MfaResponses.EnrollmentResponse enroll(final String username, final String currentPassword) {
        requirePassword(username, currentPassword);
        MfaMethod method = methodRepository.findByUserIdAndMethodType(username, METHOD).orElseGet(MfaMethod::new);
        if (method.getStatus() == MfaMethodStatus.ACTIVE) {
            throw new IllegalStateException("TOTP is already active");
        }
        final String secret = totpService.generateSecret();
        final MfaSecretManager.Ciphertext encrypted = secretManager.encrypt(secret);
        method.setUserId(username);
        method.setMethodType(METHOD);
        method.setSecretCiphertext(encrypted.value());
        method.setSecretKeyId(encrypted.keyId());
        method.setStatus(MfaMethodStatus.PENDING);
        method.setVerifiedAt(null);
        method = methodRepository.save(method);
        recoveryCodeRepository.deleteByMfaMethodId(method.getId());
        return new MfaResponses.EnrollmentResponse(secret, totpService.provisioningUri(username, secret));
    }

    @Transactional(noRollbackFor = MfaVerificationException.class)
    public MfaResponses.ConfirmationResponse confirm(final String username, final String code) {
        final MfaMethod method = requireMethod(username, MfaMethodStatus.PENDING);
        verifyTotpOrThrow(method, code);
        method.setStatus(MfaMethodStatus.ACTIVE);
        method.setVerifiedAt(Instant.now());
        methodRepository.save(method);
        final List<String> codes = replaceRecoveryCodes(method);
        return new MfaResponses.ConfirmationResponse(true, codes);
    }

    public MfaResponses.RecoveryCodesResponse regenerateRecoveryCodes(final String username, final String currentPassword) {
        requirePassword(username, currentPassword);
        return new MfaResponses.RecoveryCodesResponse(replaceRecoveryCodes(requireMethod(username, MfaMethodStatus.ACTIVE)));
    }

    @Transactional(noRollbackFor = MfaVerificationException.class)
    public void disable(final String username, final String currentPassword, final String code) {
        requirePassword(username, currentPassword);
        final MfaMethod method = requireMethod(username, MfaMethodStatus.ACTIVE);
        verifyTotpOrThrow(method, code);
        method.setStatus(MfaMethodStatus.DISABLED);
        methodRepository.save(method);
        recoveryCodeRepository.deleteByMfaMethodId(method.getId());
    }

    /**
     * For sensitive account changes: when the user has an authenticator enrolled, a valid code
     * is required (same lockout as every other TOTP check). Users without MFA pass through.
     */
    @Transactional(noRollbackFor = MfaVerificationException.class)
    public void requireCodeIfEnrolled(final String username, final String code) {
        final Optional<MfaMethod> method =
                methodRepository.findByUserIdAndMethodTypeAndStatus(username, METHOD, MfaMethodStatus.ACTIVE);
        if (method.isEmpty()) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new MfaVerificationException("An authenticator code is required");
        }
        verifyTotpOrThrow(method.get(), code);
    }

    /**
     * Verifies a TOTP code with a consecutive-failure lockout.
     *
     * <p>While locked, the secret is not decrypted or compared at all, which bounds online guessing
     * against the six-digit code space. A wrong code and a locked method raise the same message so a
     * caller cannot use the response to learn whether a guess was "closer" than another. Successful
     * verification clears the counter.
     */
    private void verifyTotpOrThrow(final MfaMethod method, final String code) {
        final Instant now = Instant.now();
        if (isLocked(method, now)) {
            throw new MfaVerificationException(INVALID_CODE_MESSAGE);
        }
        if (totpService.verify(secretManager.decrypt(method.getSecretCiphertext(), method.getSecretKeyId()),
                code, now)) {
            method.setFailedVerificationAttempts(0);
            method.setLockedUntil(null);
            return;
        }
        recordFailedAttempt(method, now);
        throw new MfaVerificationException(INVALID_CODE_MESSAGE);
    }

    private boolean isLocked(final MfaMethod method, final Instant now) {
        final Instant lockedUntil = method.getLockedUntil();
        if (lockedUntil == null) {
            return false;
        }
        if (lockedUntil.isAfter(now)) {
            return true;
        }
        // Lockout elapsed: clear it so the legitimate owner is not locked out permanently.
        method.setLockedUntil(null);
        method.setFailedVerificationAttempts(0);
        return false;
    }

    private void recordFailedAttempt(final MfaMethod method, final Instant now) {
        final int attempts = method.getFailedVerificationAttempts() + 1;
        method.setFailedVerificationAttempts(attempts);
        if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
            method.setLockedUntil(now.plus(VERIFICATION_LOCKOUT));
        }
        methodRepository.save(method);
    }

    MfaMethod activeMethod(final String username) {
        return requireMethod(username, MfaMethodStatus.ACTIVE);
    }

    boolean verifyCredential(final MfaMethod method, final String credential) {
        if (totpService.verify(secretManager.decrypt(method.getSecretCiphertext(), method.getSecretKeyId()),
                credential, Instant.now())) {
            method.setLastUsedAt(Instant.now());
            methodRepository.save(method);
            return true;
        }
        for (final MfaRecoveryCode code : recoveryCodeRepository.findByMfaMethodIdAndUsedAtIsNull(method.getId())) {
            if (passwordEncoder.matches(credential, code.getCodeHash())) {
                code.setUsedAt(Instant.now());
                recoveryCodeRepository.save(code);
                method.setLastUsedAt(Instant.now());
                methodRepository.save(method);
                return true;
            }
        }
        return false;
    }

    private MfaMethod requireMethod(final String username, final MfaMethodStatus status) {
        return methodRepository.findByUserIdAndMethodTypeAndStatus(username, METHOD, status)
                .orElseThrow(() -> new IllegalStateException("Active TOTP enrollment is required"));
    }

    private void requirePassword(final String username, final String password) {
        final User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new MfaVerificationException("Current password is invalid");
        }
    }

    private List<String> replaceRecoveryCodes(final MfaMethod method) {
        recoveryCodeRepository.deleteByMfaMethodId(method.getId());
        final List<String> rawCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            final String raw = randomRecoveryCode();
            final MfaRecoveryCode entity = new MfaRecoveryCode();
            entity.setMfaMethodId(method.getId());
            entity.setCodeHash(passwordEncoder.encode(raw));
            recoveryCodeRepository.save(entity);
            rawCodes.add(raw);
        }
        return List.copyOf(rawCodes);
    }

    private String randomRecoveryCode() {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        final StringBuilder value = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) value.append('-');
            value.append(alphabet[secureRandom.nextInt(alphabet.length)]);
        }
        return value.toString();
    }
}
