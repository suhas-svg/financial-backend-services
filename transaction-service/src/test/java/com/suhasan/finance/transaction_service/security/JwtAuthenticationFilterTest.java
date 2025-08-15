package com.suhasan.finance.transaction_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
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

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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

    private SecretKey secretKey;
    private String validToken;
    private String expiredToken;
    private String invalidToken;

    @BeforeEach
    void setUp() {
        // Create a test secret key
        secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String secretKeyString = java.util.Base64.getEncoder().encodeToString(secretKey.getEncoded());
        
        // Set the secret key in the filter using reflection
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "jwtSecret", secretKeyString);
        
        // Create valid token
        validToken = Jwts.builder()
                .setSubject("user123")
                .claim("userId", "user123")
                .claim("username", "testuser")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 24 hours
                .signWith(secretKey)
                .compact();
        
        // Create expired token
        expiredToken = Jwts.builder()
                .setSubject("user123")
                .claim("userId", "user123")
                .claim("username", "testuser")
                .setIssuedAt(new Date(System.currentTimeMillis() - 86400000)) // 24 hours ago
                .setExpiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(secretKey)
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
    void doFilterInternal_TokenWithoutUserId_ContinuesFilterChain() throws ServletException, IOException {
        // Arrange
        String tokenWithoutUserId = Jwts.builder()
                .setSubject("user123")
                .claim("username", "testuser")
                // Missing userId claim
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(secretKey)
                .compact();
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + tokenWithoutUserId);

        // Act
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNull(authentication);
        
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
}