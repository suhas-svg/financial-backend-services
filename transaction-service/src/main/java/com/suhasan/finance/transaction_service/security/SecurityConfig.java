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
                        // â”€â”€ Public read-only health probes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        .requestMatchers("/api/transactions/health").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // â”€â”€ Privileged actuator endpoints (Prometheus scrape, metrics) â”€â”€â”€â”€â”€â”€â”€
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                        .hasAnyRole("ADMIN", "INTERNAL_SERVICE")

                        // â”€â”€ Monitoring API â€” internal/admin only (H1 fix) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        // Previously .authenticated() â€” any user could read circuit-breaker state
                        // and alert thresholds. Now restricted to privileged roles only.
                        .requestMatchers("/api/monitoring/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")
                        .requestMatchers("/api/audit/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")
                        .requestMatchers("/api/risk/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")
                        .requestMatchers("/api/investigations/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")
                        .requestMatchers("/api/admin/outcome-protection/guardrails",
                                "/api/admin/outcome-protection/guardrails/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/ledger/bootstrap", "/api/admin/ledger/bootstrap/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/ledger/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")
                        .requestMatchers("/api/admin/reconciliation/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")
                        .requestMatchers("/api/disputes/admin/**").hasAnyRole("ADMIN", "INTERNAL_SERVICE")
                        .requestMatchers("/api/disputes/**").authenticated()

                        // â”€â”€ Transaction endpoints â€” require authenticated user â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        .requestMatchers("/api/scheduled-transfers/**").authenticated()
                        .requestMatchers("/api/outcome-protection/**").authenticated()
                        .requestMatchers("/api/transactions/**").authenticated()
                        .requestMatchers("/api/ledger/**").authenticated()

                        // â”€â”€ Catch-all â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
