package com.suhasan.finance.transaction_service.security;

import com.suhasan.finance.transaction_service.security.keys.TestKeys;
import java.security.PrivateKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class JwtAuthenticationFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private PrivateKey secretKey;
    private String validToken;
    private String expiredToken;
    private String invalidToken;

    @BeforeEach
    void setUp() {
        // account-service signs user tokens with RS256; the filter verifies with the public key.
        secretKey = TestKeys.USER.getPrivate();
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "publicKeyPem",
                TestKeys.publicPem(TestKeys.USER.getPublic()));
        
        // Create valid token
        validToken = Jwts.builder()
                .subject("user123")
                .claim("userId", "user123")
                .claim("username", "testuser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24 hours
                .signWith(secretKey, Jwts.SIG.RS256)
                .compact();
        
        // Create expired token
        expiredToken = Jwts.builder()
                .subject("user123")
                .claim("userId", "user123")
                .claim("username", "testuser")
                .issuedAt(new Date(System.currentTimeMillis() - 86400000)) // 24 hours ago
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(secretKey, Jwts.SIG.RS256)
                .compact();
        
        // Create invalid token
        invalidToken = "invalid.jwt.token";
        
        // Clear security context
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_ValidToken_SetsAuthentication() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(request.getRequestURI()).thenReturn("/api/transactions/transfer");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("user123", authentication.getName());
        assertTrue(authentication.isAuthenticated());
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_NoAuthorizationHeader_ContinuesFilterChain() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidAuthorizationHeader_ContinuesFilterChain() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Basic invalid");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ExpiredToken_ContinuesFilterChain() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidToken_ContinuesFilterChain() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + invalidToken);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_EmptyToken_ContinuesFilterChain() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_PublicEndpoint_SkipsAuthentication() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/transactions/health");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        // Should still set authentication even for public endpoints if valid token is provided
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("user123", authentication.getName());
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ActuatorEndpoint_SkipsAuthentication() throws ServletException, IOException {
        // Arrange
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_TokenWithoutUserId_AuthenticatesUsingSubject() throws ServletException, IOException {
        // Arrange
        String tokenWithoutUserId = Jwts.builder()
                .subject("user123")
                .claim("username", "testuser")
                // Missing userId claim
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(secretKey, Jwts.SIG.RS256)
                .compact();
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + tokenWithoutUserId);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("user123", authentication.getName());
        assertTrue(authentication.isAuthenticated());
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_AlreadyAuthenticated_SkipsAuthentication() throws ServletException, IOException {
        // Arrange
        SecurityContextHolder.setContext(securityContext);
        Authentication existingAuth = mock(Authentication.class);
        when(securityContext.getAuthentication()).thenReturn(existingAuth);
        when(existingAuth.isAuthenticated()).thenReturn(true);
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        // Should not create new authentication since one already exists
    }

    @Test
    void extractTokenFromHeader_ValidBearerToken_ReturnsToken() {
        // Arrange
        String authHeader = "Bearer " + validToken;

        // Act
        String extractedToken = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "extractTokenFromHeader", authHeader);

        // Assert
        assertEquals(validToken, extractedToken);
    }

    @Test
    void extractTokenFromHeader_InvalidHeader_ReturnsNull() {
        // Arrange
        String authHeader = "Basic invalid";

        // Act
        String extractedToken = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "extractTokenFromHeader", authHeader);

        // Assert
        assertNull(extractedToken);
    }

    @Test
    void extractTokenFromHeader_NullHeader_ReturnsNull() {
        // Act
        String extractedToken = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "extractTokenFromHeader", (String) null);

        // Assert
        assertNull(extractedToken);
    }

    @Test
    void validateToken_ValidToken_ReturnsTrue() {
        // Act
        boolean isValid = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "validateToken", validToken);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void validateToken_ExpiredToken_ReturnsFalse() {
        // Act
        boolean isValid = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "validateToken", expiredToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void validateToken_InvalidToken_ReturnsFalse() {
        // Act
        boolean isValid = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "validateToken", invalidToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void validateToken_NullToken_ReturnsFalse() {
        // Act
        boolean isValid = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "validateToken", (String) null);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void extractClaims_ValidToken_ReturnsClaims() {
        // Act
        Claims claims = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "extractClaims", validToken);

        // Assert
        assertNotNull(claims);
        assertEquals("user123", claims.getSubject());
        assertEquals("user123", claims.get("userId"));
        assertEquals("testuser", claims.get("username"));
    }

    @Test
    void extractClaims_InvalidToken_ReturnsNull() {
        // Act
        Claims claims = ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "extractClaims", invalidToken);

        // Assert
        assertNull(claims);
    }

    @Test
    void doFilterInternal_Hs256TokenSignedWithThePublicKey_IsRejected() throws ServletException, IOException {
        byte[] publicKeyAsSecret = java.util.Base64.getEncoder().encode(TestKeys.USER.getPublic().getEncoded());
        String confused = Jwts.builder().subject("attacker")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(publicKeyAsSecret), Jwts.SIG.HS256)
                .compact();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + confused);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
