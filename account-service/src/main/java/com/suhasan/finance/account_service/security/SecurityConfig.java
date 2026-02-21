package com.suhasan.finance.account_service.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Dependencies are injected and managed by Spring")
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Auth (login, register) — always public ─────────────────────────
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── Health READ endpoints — safe for load balancers/probes ─────────
                        // GET /api/health/ping and GET /api/health/status are read-only.
                        // (H2 fix: POST /api/health/check and POST /api/health/deployment were
                        // previously reachable anonymously via the wildcarded permitAll below.
                        // They are now restricted to privileged callers only.)
                        .requestMatchers(HttpMethod.GET, "/api/health/ping", "/api/health/status").permitAll()

                        // ── Actuator read-only probes — public (for K8s liveness/readiness) ─
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                        // ── Privileged actuator ─────────────────────────────────────────────
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                        .hasAnyRole("ADMIN", "INTERNAL_SERVICE")

                        // ── Internal service-to-service API ────────────────────────────────
                        .requestMatchers("/api/internal/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")

                        // ── Remaining health endpoints (POST /check, POST /deployment, /metrics)
                        // restricted to privileged roles (H2 fix) ──────────────────────
                        .requestMatchers("/api/health/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")

                        // ── Everything else requires authentication ─────────────────────────
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
