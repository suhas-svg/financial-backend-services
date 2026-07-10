package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.MfaResponses;
import com.suhasan.finance.account_service.entity.MfaMethod;
import com.suhasan.finance.account_service.entity.MfaMethodStatus;
import com.suhasan.finance.account_service.entity.MfaRecoveryCode;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.exception.MfaVerificationException;
import com.suhasan.finance.account_service.repository.MfaMethodRepository;
import com.suhasan.finance.account_service.repository.MfaRecoveryCodeRepository;
import com.suhasan.finance.account_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MfaService {
    private static final String METHOD = "TOTP";
    private static final int RECOVERY_CODE_COUNT = 8;
    private final MfaMethodRepository methodRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretEncryptionService encryptionService;
    private final TotpService totpService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public MfaResponses.StatusResponse status(String username) {
        return methodRepository.findByUserIdAndMethodType(username, METHOD)
                .map(method -> new MfaResponses.StatusResponse(
                        method.getStatus() == MfaMethodStatus.ACTIVE,
                        method.getStatus().name(),
                        recoveryCodeRepository.countByMfaMethodIdAndUsedAtIsNull(method.getId())))
                .orElseGet(() -> new MfaResponses.StatusResponse(false, "NOT_ENROLLED", 0));
    }

    public MfaResponses.EnrollmentResponse enroll(String username, String currentPassword) {
        requirePassword(username, currentPassword);
        MfaMethod method = methodRepository.findByUserIdAndMethodType(username, METHOD).orElseGet(MfaMethod::new);
        if (method.getStatus() == MfaMethodStatus.ACTIVE) {
            throw new IllegalStateException("TOTP is already active");
        }
        String secret = totpService.generateSecret();
        method.setUserId(username);
        method.setMethodType(METHOD);
        method.setSecretCiphertext(encryptionService.encrypt(secret));
        method.setStatus(MfaMethodStatus.PENDING);
        method.setVerifiedAt(null);
        method = methodRepository.save(method);
        recoveryCodeRepository.deleteByMfaMethodId(method.getId());
        return new MfaResponses.EnrollmentResponse(secret, totpService.provisioningUri(username, secret));
    }

    public MfaResponses.ConfirmationResponse confirm(String username, String code) {
        MfaMethod method = requireMethod(username, MfaMethodStatus.PENDING);
        if (!totpService.verify(encryptionService.decrypt(method.getSecretCiphertext()), code, Instant.now())) {
            throw new MfaVerificationException("Invalid authentication code");
        }
        method.setStatus(MfaMethodStatus.ACTIVE);
        method.setVerifiedAt(Instant.now());
        methodRepository.save(method);
        List<String> codes = replaceRecoveryCodes(method);
        return new MfaResponses.ConfirmationResponse(true, codes);
    }

    public MfaResponses.RecoveryCodesResponse regenerateRecoveryCodes(String username, String currentPassword) {
        requirePassword(username, currentPassword);
        return new MfaResponses.RecoveryCodesResponse(replaceRecoveryCodes(requireMethod(username, MfaMethodStatus.ACTIVE)));
    }

    public void disable(String username, String currentPassword, String code) {
        requirePassword(username, currentPassword);
        MfaMethod method = requireMethod(username, MfaMethodStatus.ACTIVE);
        if (!totpService.verify(encryptionService.decrypt(method.getSecretCiphertext()), code, Instant.now())) {
            throw new MfaVerificationException("Invalid authentication code");
        }
        method.setStatus(MfaMethodStatus.DISABLED);
        methodRepository.save(method);
        recoveryCodeRepository.deleteByMfaMethodId(method.getId());
    }

    MfaMethod activeMethod(String username) {
        return requireMethod(username, MfaMethodStatus.ACTIVE);
    }

    boolean verifyCredential(MfaMethod method, String credential) {
        if (totpService.verify(encryptionService.decrypt(method.getSecretCiphertext()), credential, Instant.now())) {
            method.setLastUsedAt(Instant.now());
            methodRepository.save(method);
            return true;
        }
        for (MfaRecoveryCode code : recoveryCodeRepository.findByMfaMethodIdAndUsedAtIsNull(method.getId())) {
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

    private MfaMethod requireMethod(String username, MfaMethodStatus status) {
        return methodRepository.findByUserIdAndMethodTypeAndStatus(username, METHOD, status)
                .orElseThrow(() -> new IllegalStateException("Active TOTP enrollment is required"));
    }

    private void requirePassword(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new MfaVerificationException("Current password is invalid");
        }
    }

    private List<String> replaceRecoveryCodes(MfaMethod method) {
        recoveryCodeRepository.deleteByMfaMethodId(method.getId());
        List<String> rawCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = randomRecoveryCode();
            MfaRecoveryCode entity = new MfaRecoveryCode();
            entity.setMfaMethodId(method.getId());
            entity.setCodeHash(passwordEncoder.encode(raw));
            recoveryCodeRepository.save(entity);
            rawCodes.add(raw);
        }
        return List.copyOf(rawCodes);
    }

    private String randomRecoveryCode() {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        StringBuilder value = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) value.append('-');
            value.append(alphabet[secureRandom.nextInt(alphabet.length)]);
        }
        return value.toString();
    }
}
