package com.suhasan.finance.transaction_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SyntheticSandboxSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain syntheticMetadataEndpoint(HttpSecurity http) throws Exception {
        http.securityMatcher(new AntPathRequestMatcher("/api/sandbox/metadata", HttpMethod.GET.name()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
