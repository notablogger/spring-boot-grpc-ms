package com.example.paymentservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Spring Boot's native gRPC support auto-configures the authentication and
 * authorization interceptors itself (from the standard OAuth2 resource-server
 * config below plus {@code @PreAuthorize} on {@link com.example.paymentservice.grpc.PaymentGrpcService}),
 * so all that's left here is the IdP-specific glue: mapping Keycloak's
 * {@code realm_access.roles} claim to Spring Security authorities.
 */
@Configuration
@EnableMethodSecurity
public class GrpcSecurityConfig {

    // payment-service has no spring-web on its classpath, so Spring Boot's
    // OAuth2ResourceServerAutoConfiguration (which would otherwise expose a
    // JwtDecoder bean from spring.security.oauth2.resourceserver.jwt.issuer-uri
    // automatically) never activates. Binding the same standard property
    // directly and building the decoder via Spring Security's own
    // JwtDecoders factory keeps this on stock APIs regardless.
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
