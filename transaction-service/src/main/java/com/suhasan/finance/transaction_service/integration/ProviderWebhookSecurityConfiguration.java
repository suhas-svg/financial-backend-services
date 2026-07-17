package com.suhasan.finance.transaction_service.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The webhook endpoint authenticates with provider-specific signatures rather
 * than customer/operator JWTs. The verifier is fail closed until a named
 * provider adapter is supplied.
 */
@Configuration
public class ProviderWebhookSecurityConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain providerWebhookSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/provider-activations/*/webhooks")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
