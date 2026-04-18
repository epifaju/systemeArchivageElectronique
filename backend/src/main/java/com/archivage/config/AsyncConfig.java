package com.archivage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "ocrExecutor")
    public Executor ocrExecutor(AppOcrProperties ocrProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, ocrProperties.workers()));
        executor.setMaxPoolSize(Math.max(1, ocrProperties.workers()));
        executor.setThreadNamePrefix("ocr-");
        executor.initialize();
        return executor;
    }
}
