package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AuthRequest;
import com.suhasan.finance.account_service.dto.AuthResponse;
import com.suhasan.finance.account_service.security.ClientIpResolver;
import com.suhasan.finance.account_service.security.JwtTokenProvider;
import com.suhasan.finance.account_service.security.RefreshCookies;
import com.suhasan.finance.account_service.security.RefreshTokenService;
import com.suhasan.finance.account_service.security.RefreshTokenService.IssuedToken;
import com.suhasan.finance.account_service.security.RefreshTokenService.Refresh;
import com.suhasan.finance.account_service.service.AuthService;
import com.suhasan.finance.account_service.service.AuthThrottleService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthControllerSessionTest {

    private AuthenticationManager authManager;
    private JwtTokenProvider tokenProvider;
    private RefreshTokenService refreshTokens;
    private UserDetailsService users;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authManager = mock(AuthenticationManager.class);
        tokenProvider = mock(JwtTokenProvider.class);
        refreshTokens = mock(RefreshTokenService.class);
        users = mock(UserDetailsService.class);
        controller = new AuthController(authManager, tokenProvider, mock(AuthService.class),
                mock(AuthThrottleService.class), new ClientIpResolver(""), refreshTokens,
                new RefreshCookies("fc_refresh", "/account-api/api/auth", true), users);
    }

    @Test
    void loginSetsHardenedRefreshCookieAndStillReturnsToken() {
        var auth = UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of());
        when(authManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("access");
        when(refreshTokens.issue("alice")).thenReturn(new IssuedToken("r1", Instant.now().plusSeconds(1800)));

        AuthRequest req = new AuthRequest();
        req.setUsername("alice");
        req.setPassword("secret");
        ResponseEntity<AuthResponse> response = controller.login(req, new MockHttpServletRequest());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isEqualTo("access");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("fc_refresh=r1", "HttpOnly", "Secure", "SameSite=Strict", "Path=/account-api/api/auth");
    }

    @Test
    void refreshRequiresCustomHeader() {
        ResponseEntity<?> response = controller.refresh(null, withCookie("r1"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void refreshIssuesAccessTokenAndRotatesCookie() {
        when(refreshTokens.refresh("r1")).thenReturn(Optional.of(
                new Refresh("alice", new IssuedToken("r2", Instant.now().plusSeconds(1800)))));
        when(users.loadUserByUsername("alice")).thenReturn(
                new User("alice", "x", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(tokenProvider.generateToken(any())).thenReturn("access-2");

        ResponseEntity<?> response = controller.refresh("XMLHttpRequest", withCookie("r1"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((AuthResponse) response.getBody()).getAccessToken()).isEqualTo("access-2");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("fc_refresh=r2");
    }

    @Test
    void invalidRefreshClearsCookie() {
        when(refreshTokens.refresh("stale")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.refresh("XMLHttpRequest", withCookie("stale"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("fc_refresh=", "Max-Age=0");
    }

    @Test
    void refreshForDeletedUserEndsSession() {
        when(refreshTokens.refresh("r1")).thenReturn(Optional.of(
                new Refresh("ghost", new IssuedToken("r2", Instant.now().plusSeconds(1800)))));
        when(users.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("gone"));

        assertThat(controller.refresh("XMLHttpRequest", withCookie("r1")).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void logoutRevokesAndClearsCookie() {
        ResponseEntity<?> response = controller.logout("XMLHttpRequest", withCookie("r1"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(refreshTokens).revoke("r1");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    private static MockHttpServletRequest withCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("fc_refresh", value));
        return request;
    }
}
