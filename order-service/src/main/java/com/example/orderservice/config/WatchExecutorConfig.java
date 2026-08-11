package com.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class WatchExecutorConfig {

    // Each watch is a short-lived, IO-bound task (block on stream reads,
    // occasionally sleep between updates) -- a good fit for a virtual
    // thread per task rather than a fixed-size platform-thread pool.
    // ExecutorService (not just Executor) so PaymentStatusWatchService can
    // hold onto each watch's Future and cancel it on request.
    @Bean
    ExecutorService watchExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
