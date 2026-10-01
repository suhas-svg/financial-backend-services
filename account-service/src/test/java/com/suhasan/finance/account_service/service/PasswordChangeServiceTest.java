package com.suhasan.finance.account_service.service;

import com.suhasan.finance.account_service.entity.User;
import com.suhasan.finance.account_service.exception.MfaVerificationException;
import com.suhasan.finance.account_service.exception.TooManyAttemptsException;
import com.suhasan.finance.account_service.repository.UserRepository;
import com.suhasan.finance.account_service.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordChangeServiceTest {

    private static final String CURRENT = "correct horse battery";
    private static final String NEW = "a much longer new passphrase";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository users = mock(UserRepository.class);
    private final MfaService mfa = mock(MfaService.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AuthThrottleService throttle = mock(AuthThrottleService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PasswordChangeService service =
            new PasswordChangeService(users, encoder, mfa, refreshTokens, throttle, notifications);
    private User alice;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setUsername("alice");
        alice.setPassword(encoder.encode(CURRENT));
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(refreshTokens.revokeAll("alice", PasswordChangeService.REVOKE_REASON)).thenReturn(3);
    }

    @Test
    void changesThePasswordAndEndsEverySession() {
        int ended = service.changePassword("alice", CURRENT, NEW, "123456", "203.0.113.7");

        assertThat(ended).isEqualTo(3);
        assertThat(encoder.matches(NEW, alice.getPassword())).isTrue();
        verify(users).save(alice);
        verify(mfa).requireCodeIfEnrolled("alice", "123456");
        verify(refreshTokens).revokeAll("alice", PasswordChangeService.REVOKE_REASON);
        verify(throttle).recordLoginSuccess("alice");
        verify(notifications).createInternal(any());
    }

    @Test
    void aWrongCurrentPasswordCountsAsALoginFailureAndChangesNothing() {
        assertThatThrownBy(() -> service.changePassword("alice", "guess", NEW, null, "203.0.113.7"))
                .isInstanceOf(MfaVerificationException.class)
                .hasMessage("Current password is invalid");

        verify(throttle).recordLoginFailure("alice", "203.0.113.7");
        verify(users, never()).save(any());
        verify(refreshTokens, never()).revokeAll(any(), any());
        assertThat(encoder.matches(CURRENT, alice.getPassword())).isTrue();
    }

    @Test
    void aLockedOutUserCannotKeepGuessingThroughThisEndpoint() {
        doThrow(new TooManyAttemptsException(900)).when(throttle).assertLoginAllowed("alice", "203.0.113.7");

        assertThatThrownBy(() -> service.changePassword("alice", CURRENT, NEW, null, "203.0.113.7"))
                .isInstanceOf(TooManyAttemptsException.class);

        verify(users, never()).findByUsername(any());
        verify(users, never()).save(any());
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentOne() {
        assertThatThrownBy(() -> service.changePassword("alice", CURRENT, CURRENT, null, "203.0.113.7"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must differ");

        verify(users, never()).save(any());
    }

    @Test
    void anEnrolledAuthenticatorMustConfirmTheChange() {
        doThrow(new MfaVerificationException("An authenticator code is required"))
                .when(mfa).requireCodeIfEnrolled("alice", null);

        assertThatThrownBy(() -> service.changePassword("alice", CURRENT, NEW, null, "203.0.113.7"))
                .isInstanceOf(MfaVerificationException.class);

        verify(users, never()).save(any());
        verify(refreshTokens, never()).revokeAll(any(), any());
    }

    @Test
    void aFailedNotificationDoesNotUndoTheChange() {
        when(notifications.createInternal(any())).thenThrow(new IllegalStateException("notifications down"));

        assertThat(service.changePassword("alice", CURRENT, NEW, null, "203.0.113.7")).isEqualTo(3);
        assertThat(encoder.matches(NEW, alice.getPassword())).isTrue();
    }
}
