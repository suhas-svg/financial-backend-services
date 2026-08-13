package com.suhasan.finance.transaction_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;

@Configuration
public class CustomerNotificationExecutorConfiguration {

    @Bean(name = "customerNotificationExecutor")
    public ThreadPoolTaskExecutor customerNotificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("customer-notification-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("Customer notification executor is shut down");
            }
            task.run();
        });
        return executor;
    }
}
