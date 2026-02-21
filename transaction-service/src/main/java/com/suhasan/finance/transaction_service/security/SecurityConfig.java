package com.suhasan.finance.transaction_service.security;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> response
                                .sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response
                                .sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")))
                .authorizeHttpRequests(authz -> authz
                        // ── Public read-only health probes ────────────────────────────────────
                        .requestMatchers("/api/transactions/health").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // ── Privileged actuator endpoints (Prometheus scrape, metrics) ───────
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                        .hasAnyRole("ADMIN", "INTERNAL_SERVICE")

                        // ── Monitoring API — internal/admin only (H1 fix) ────────────────────
                        // Previously .authenticated() — any user could read circuit-breaker state
                        // and alert thresholds. Now restricted to privileged roles only.
                        .requestMatchers("/api/monitoring/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")

                        // ── Transaction endpoints — require authenticated user ────────────────
                        .requestMatchers("/api/transactions/**").authenticated()

                        // ── Catch-all ────────────────────────────────────────────────────────
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
