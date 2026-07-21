package com.suhasan.finance.account_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
public class SyntheticSandboxSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain syntheticPublicEndpoints(HttpSecurity http) throws Exception {
        http.securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/api/sandbox/metadata", HttpMethod.GET.name()),
                        new AntPathRequestMatcher("/api/sandbox/bootstrap/status", HttpMethod.GET.name()),
                        new AntPathRequestMatcher("/api/sandbox/bootstrap", HttpMethod.POST.name())))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
