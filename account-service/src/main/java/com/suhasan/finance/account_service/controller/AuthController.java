package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AuthRequest;
import com.suhasan.finance.account_service.dto.AuthResponse;
import com.suhasan.finance.account_service.dto.RegisterRequest;
import com.suhasan.finance.account_service.exception.ApiProblems;
import com.suhasan.finance.account_service.dto.RegisterResponse;
import com.suhasan.finance.account_service.security.ClientIpResolver;
import com.suhasan.finance.account_service.security.JwtTokenProvider;
import com.suhasan.finance.account_service.security.RefreshCookies;
import com.suhasan.finance.account_service.security.RefreshTokenService;
import com.suhasan.finance.account_service.service.AuthService;
import com.suhasan.finance.account_service.service.AuthThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider tokenProvider;
    private final AuthService authService;
    private final AuthThrottleService authThrottle;
    private final ClientIpResolver clientIpResolver;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies refreshCookies;
    private final UserDetailsService userDetailsService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody final AuthRequest req,
                                              final HttpServletRequest request) {
        final String clientIp = clientIpResolver.resolve(request);
        authThrottle.assertLoginAllowed(req.getUsername(), clientIp);
        final Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );
        } catch (AuthenticationException e) {
            authThrottle.recordLoginFailure(req.getUsername(), clientIp);
            throw e;
        }
        authThrottle.recordLoginSuccess(req.getUsername());
        final String token = tokenProvider.generateToken(auth);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(refreshTokens.issue(auth.getName())))
                .body(new AuthResponse(token));
    }

    /**
     * Exchanges the refresh cookie for a new access token, rotating the cookie.
     * The X-Requested-With header cannot be sent cross-site without a CORS
     * preflight, which this service never grants, adding to SameSite=Strict.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestHeader(name = "X-Requested-With", required = false) final String requestedWith,
            final HttpServletRequest request) {
        if (requestedWith == null || requestedWith.isBlank()) {
            return error(HttpStatus.FORBIDDEN, "Forbidden", "Missing X-Requested-With header", request);
        }
        final var refreshed = refreshCookies.read(request).flatMap(refreshTokens::refresh);
        if (refreshed.isEmpty()) {
            return sessionExpired(request);
        }
        final UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(refreshed.get().username());
        } catch (UsernameNotFoundException e) {
            return sessionExpired(request);
        }
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            return sessionExpired(request);
        }
        final String token = tokenProvider.generateToken(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(refreshed.get().next()))
                .body(new AuthResponse(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(name = "X-Requested-With", required = false) final String requestedWith,
            final HttpServletRequest request) {
        if (requestedWith == null || requestedWith.isBlank()) {
            return error(HttpStatus.FORBIDDEN, "Forbidden", "Missing X-Requested-With header", request);
        }
        refreshCookies.read(request).ifPresent(refreshTokens::revoke);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.clear()).build();
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody final RegisterRequest req,
                                                     final HttpServletRequest request) {
        authThrottle.checkAndRecordRegistration(clientIpResolver.resolve(request));
        final RegisterResponse response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private ResponseEntity<ProblemDetail> sessionExpired(final HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblems.problem(HttpStatus.UNAUTHORIZED, "session-expired", "Unauthorized",
                        "Session expired", request));
    }

    private static ResponseEntity<ProblemDetail> error(final HttpStatus status, final String error,
                                                       final String message, final HttpServletRequest request) {
        return ApiProblems.response(status, "forbidden", error, message, request);
    }
}
