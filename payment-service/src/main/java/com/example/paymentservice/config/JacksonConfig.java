package com.example.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * payment-service has no web dependency, so Spring Boot's Jackson
 * auto-configuration (which requires {@code spring-web} on the classpath)
 * never activates. Provide the {@link ObjectMapper} bean explicitly instead.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
