package com.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class WatchExecutorConfig {

    // Each watch is a short-lived, IO-bound task (block on stream reads,
    // occasionally sleep between updates) -- a good fit for a virtual
    // thread per task rather than a fixed-size platform-thread pool.
    @Bean
    Executor watchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
