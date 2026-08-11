package com.example.orderservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;

@Configuration
public class GrpcClientSecurityConfig {

    @Bean
    GrpcChannelBuilderCustomizer<?> jwtRelayChannelCustomizer() {
        return GrpcChannelBuilderCustomizer.matching(
                "paymentService", builder -> builder.intercept(new JwtRelayClientInterceptor()));
    }
}
