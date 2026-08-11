package com.example.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Backs the simulated payment-settling delay in
 * {@code PaymentGrpcService.watchPaymentStatus} — a stand-in for whatever
 * would push real payment-status change events (e.g. a message broker
 * subscription) in a non-demo system.
 */
@Configuration
public class PaymentWatchConfig {

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService paymentWatchScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "payment-watch-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }
}
