package com.example.pulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

    /** Runs the HTTP checks in parallel so one slow site never delays the others. */
    @Bean(name = "checkExecutor")
    public ThreadPoolTaskExecutor checkExecutor(@Value("${app.monitor.check-threads}") int threads) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(threads);
        e.setMaxPoolSize(threads);
        e.setQueueCapacity(500);
        e.setThreadNamePrefix("check-");
        return e;
    }

    /** Sends alerts (email / webhooks) without blocking the checks. */
    @Bean(name = "alertExecutor")
    public ThreadPoolTaskExecutor alertExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(100);
        e.setThreadNamePrefix("alert-");
        return e;
    }
}
