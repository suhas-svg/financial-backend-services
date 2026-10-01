package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AuthRequest;
import com.suhasan.finance.account_service.dto.AuthResponse;
import com.suhasan.finance.account_service.dto.RegisterRequest;
import com.suhasan.finance.account_service.dto.RegisterResponse;
import com.suhasan.finance.account_service.security.ClientIpResolver;
import com.suhasan.finance.account_service.security.JwtTokenProvider;
import com.suhasan.finance.account_service.service.AuthService;
import com.suhasan.finance.account_service.service.AuthThrottleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
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
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody final RegisterRequest req,
                                                     final HttpServletRequest request) {
        authThrottle.checkAndRecordRegistration(clientIpResolver.resolve(request));
        final RegisterResponse response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
