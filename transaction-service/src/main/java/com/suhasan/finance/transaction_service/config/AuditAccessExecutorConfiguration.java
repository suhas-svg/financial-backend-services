package com.suhasan.finance.transaction_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;

@Configuration
public class AuditAccessExecutorConfiguration {

    @Bean(name = "apiAccessAuditExecutor")
    public ThreadPoolTaskExecutor apiAccessAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("api-access-audit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("API access audit executor is shut down");
            }
            task.run();
        });
        return executor;
    }
}
