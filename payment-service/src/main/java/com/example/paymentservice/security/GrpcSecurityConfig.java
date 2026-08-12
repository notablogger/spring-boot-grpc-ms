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
 * {@code realm_access.roles} claim to Spring Security authorities. The REST
 * side ({@code WebSecurityConfig}) reuses the same {@link JwtDecoder} and
 * {@link KeycloakRealmRoleConverter} beans defined here.
 */
@Configuration
@EnableMethodSecurity
public class GrpcSecurityConfig {

    private final KeycloakRealmRoleConverter keycloakRealmRoleConverter;

    public GrpcSecurityConfig(KeycloakRealmRoleConverter keycloakRealmRoleConverter) {
        this.keycloakRealmRoleConverter = keycloakRealmRoleConverter;
    }

    // Built manually (rather than relying on Spring Boot's own
    // OAuth2ResourceServerAutoConfiguration) so the gRPC security wiring
    // above doesn't depend on spring-boot-starter-webmvc being present --
    // it's only on the classpath now for the separate REST admin API, and
    // dropping it shouldn't silently break gRPC auth. @ConditionalOnMissingBean
    // on Boot's own autoconfigured JwtDecoder means this bean, once
    // registered, is simply the one both the gRPC and REST security config
    // end up using.
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRealmRoleConverter);
        return converter;
    }
}
