package com.suhasan.finance.transaction_service.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderWebhookVerifierConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProviderWebhookVerifier.class)
    ProviderWebhookVerifier failClosedProviderWebhookVerifier() {
        return new FailClosedProviderWebhookVerifier();
    }
}
