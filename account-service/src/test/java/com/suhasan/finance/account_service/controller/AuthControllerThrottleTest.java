package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AuthRequest;
import com.suhasan.finance.account_service.dto.RegisterRequest;
import com.suhasan.finance.account_service.exception.TooManyAttemptsException;
import com.suhasan.finance.account_service.security.ClientIpResolver;
import com.suhasan.finance.account_service.security.JwtTokenProvider;
import com.suhasan.finance.account_service.security.RefreshCookies;
import com.suhasan.finance.account_service.security.RefreshTokenService;
import com.suhasan.finance.account_service.service.AuthService;
import com.suhasan.finance.account_service.service.AuthThrottleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthControllerThrottleTest {

    private AuthenticationManager authManager;
    private JwtTokenProvider tokenProvider;
    private AuthService authService;
    private AuthThrottleService throttle;
    private AuthController controller;
    private MockHttpServletRequest http;

    @BeforeEach
    void setUp() {
        authManager = mock(AuthenticationManager.class);
        tokenProvider = mock(JwtTokenProvider.class);
        authService = mock(AuthService.class);
        throttle = mock(AuthThrottleService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        when(refreshTokens.issue(any())).thenReturn(
                new RefreshTokenService.IssuedToken("refresh", java.time.Instant.now().plusSeconds(1800)));
        controller = new AuthController(authManager, tokenProvider, authService, throttle, new ClientIpResolver(""),
                refreshTokens, new RefreshCookies("fc_refresh", "/account-api/api/auth", true),
                mock(UserDetailsService.class));
        http = new MockHttpServletRequest();
        http.setRemoteAddr("203.0.113.9");
    }

    @Test
    void lockedLoginIsRefusedBeforeCredentialsAreChecked() {
        doThrow(new TooManyAttemptsException(60)).when(throttle).assertLoginAllowed("alice", "203.0.113.9");

        assertThatThrownBy(() -> controller.login(login("alice", "secret"), http))
                .isInstanceOf(TooManyAttemptsException.class);
        verifyNoInteractions(authManager);
    }

    @Test
    void failedLoginIsRecordedAndStillRejected() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> controller.login(login("alice", "wrong"), http))
                .isInstanceOf(BadCredentialsException.class);
        verify(throttle).recordLoginFailure("alice", "203.0.113.9");
        verify(throttle, never()).recordLoginSuccess(any());
    }

    @Test
    void successfulLoginClearsFailures() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", null, java.util.List.of());
        when(authManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("token");

        assertThat(controller.login(login("alice", "secret"), http).getStatusCode().value()).isEqualTo(200);
        verify(throttle).recordLoginSuccess("alice");
        verify(throttle, never()).recordLoginFailure(any(), any());
    }

    @Test
    void lockedRegistrationIsRefusedBeforeAnyUserIsCreated() {
        doThrow(new TooManyAttemptsException(60)).when(throttle).checkAndRecordRegistration("203.0.113.9");

        assertThatThrownBy(() -> controller.register(new RegisterRequest(), http))
                .isInstanceOf(TooManyAttemptsException.class);
        verifyNoInteractions(authService);
    }

    private static AuthRequest login(String username, String password) {
        AuthRequest request = new AuthRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
