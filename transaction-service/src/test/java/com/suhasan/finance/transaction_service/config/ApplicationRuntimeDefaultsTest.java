package com.suhasan.finance.transaction_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRuntimeDefaultsTest {

    @Test
    void accountServiceTimeoutDefaultsToThirtySeconds() throws Exception {
        Properties properties = PropertiesLoaderUtils.loadAllProperties("application.properties");

        assertThat(properties.getProperty("account-service.timeout"))
                .isEqualTo("${ACCOUNT_SERVICE_TIMEOUT:30000}");
        assertThat(properties.getProperty("account-service.resilience.time-limiter.timeout"))
                .isEqualTo("${ACCOUNT_SERVICE_TIMEOUT:30000}");
    }
}
