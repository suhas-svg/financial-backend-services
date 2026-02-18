package com.suhasan.finance.transaction_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${security.jwt.secret}")
    private String jwtSecret;
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String token = extractTokenFromHeader(request.getHeader("Authorization"));
        Authentication existingAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuthentication != null && existingAuthentication.isAuthenticated()) {
            log.debug("Authentication already present for request URI: {}", requestUri);
            filterChain.doFilter(request, response);
            return;
        }
        
        if (token != null && validateToken(token)) {
            Claims claims = extractClaims(token);

            if (claims != null) {
                String username = claims.getSubject();
                if (username == null || username.isBlank()) {
                    Object usernameClaim = claims.get("username");
                    username = usernameClaim != null ? usernameClaim.toString() : null;
                }

                if (username != null && !username.isBlank()) {
                    List<SimpleGrantedAuthority> authorities = extractRolesFromClaims(claims).stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, token, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Set authentication for user: {} with roles: {}", username, authorities);
                } else {
                    log.debug("JWT token does not contain a usable username for URI: {}", requestUri);
                }
            }
        } else {
            log.debug("No valid JWT token found in request");
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return token.isBlank() ? null : token;
        }
        return null;
    }
    
    private boolean validateToken(String token) {
        return extractClaims(token) != null;
    }

    private Claims extractClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(resolveSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }
    
    private SecretKey resolveSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    private List<String> extractRolesFromClaims(Claims claims) {
        try {
            Set<String> normalizedRoles = new LinkedHashSet<>();
            collectRoles(normalizedRoles, claims.get("roles"));
            collectRoles(normalizedRoles, claims.get("authorities"));
            collectRoles(normalizedRoles, claims.get("role"));

            if (!normalizedRoles.isEmpty()) {
                return normalizedRoles.stream().collect(Collectors.toList());
            }

            log.debug("No roles found in token, assigning default ROLE_USER");
            return List.of("ROLE_USER");
        } catch (Exception e) {
            log.debug("Error extracting roles from token: {}", e.getMessage());
            return List.of("ROLE_USER");
        }
    }

    private void collectRoles(Set<String> roleStore, Object claimValue) {
        if (claimValue instanceof List<?> roleList) {
            roleList.forEach(role -> addRole(roleStore, role != null ? role.toString() : null));
            return;
        }

        if (claimValue instanceof String roleText) {
            for (String role : roleText.split(",")) {
                addRole(roleStore, role);
            }
        }
    }

    private void addRole(Set<String> roleStore, String rawRole) {
        if (rawRole == null) {
            return;
        }

        String normalizedRole = rawRole.trim();
        if (normalizedRole.isBlank()) {
            return;
        }

        if (!normalizedRole.startsWith("ROLE_")) {
            normalizedRole = "ROLE_" + normalizedRole;
        }

        roleStore.add(normalizedRole);
    }
}
