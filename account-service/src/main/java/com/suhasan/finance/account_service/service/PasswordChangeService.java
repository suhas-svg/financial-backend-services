package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.dto.NotificationCreateRequest;
import com.suhasan.finance.account_service.entity.NotificationSeverity;
import com.suhasan.finance.account_service.entity.NotificationSourceType;
import com.suhasan.finance.account_service.entity.NotificationType;
import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.exception.MfaVerificationException;
import com.suhasan.finance.account_service.repository.UserRepository;
import com.suhasan.finance.account_service.security.RefreshTokenService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Changes a signed-in user's password and ends every session they have.
 *
 * <p>Guessing the current password through this endpoint counts against the same per-user and
 * per-IP limits as login, and an enrolled authenticator must confirm the change (with the usual
 * TOTP lockout). On success every refresh session is revoked, so other devices (including one an
 * attacker may hold) cannot renew; the caller signs in again with the new password.
 */
@Service
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Dependencies are injected and managed by Spring")
public class PasswordChangeService {

    static final String REVOKE_REASON = "PASSWORD_CHANGED";
    private static final String INVALID_CURRENT = "Current password is invalid";
    private static final Logger LOG = LoggerFactory.getLogger(PasswordChangeService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final MfaService mfaService;
    private final RefreshTokenService refreshTokens;
    private final AuthThrottleService throttle;
    private final NotificationService notifications;

    public PasswordChangeService(final UserRepository users, final PasswordEncoder passwordEncoder,
                                 final MfaService mfaService, final RefreshTokenService refreshTokens,
                                 final AuthThrottleService throttle, final NotificationService notifications) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mfaService = mfaService;
        this.refreshTokens = refreshTokens;
        this.throttle = throttle;
        this.notifications = notifications;
    }

    /** @return how many sessions were ended */
    @Transactional(noRollbackFor = MfaVerificationException.class)
    public int changePassword(final String username, final String currentPassword, final String newPassword,
                              final String mfaCode, final String clientIp) {
        throttle.assertLoginAllowed(username, clientIp);
        final User user = users.findByUsername(username)
                .orElseThrow(() -> new MfaVerificationException(INVALID_CURRENT));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throttle.recordLoginFailure(username, clientIp);
            throw new MfaVerificationException(INVALID_CURRENT);
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The new password must differ from the current one");
        }
        mfaService.requireCodeIfEnrolled(username, mfaCode);

        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        throttle.recordLoginSuccess(username);
        final int ended = refreshTokens.revokeAll(username, REVOKE_REASON);
        // After commit: a failing notification must neither undo nor be undone with the change.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyBestEffort(username);
                }
            });
        } else {
            notifyBestEffort(username);
        }
        return ended;
    }

    private void notifyBestEffort(final String username) {
        try {
            notifications.createInternal(NotificationCreateRequest.builder()
                    .userId(username)
                    .type(NotificationType.SECURITY_ALERT)
                    .severity(NotificationSeverity.WARNING)
                    .title("Your password was changed")
                    .message("All devices were signed out. If this wasn't you, contact support immediately.")
                    .sourceType(NotificationSourceType.ACCOUNT)
                    .sourceId(username)
                    .dedupeKey("password-changed:" + username + ":" + Instant.now().toEpochMilli())
                    .build());
        } catch (RuntimeException e) {
            // The change is done; a missing notification must not undo it.
            if (LOG.isWarnEnabled()) {
                LOG.warn("Password changed but the security notification failed: {}", e.getMessage());
            }
        }
    }
}
