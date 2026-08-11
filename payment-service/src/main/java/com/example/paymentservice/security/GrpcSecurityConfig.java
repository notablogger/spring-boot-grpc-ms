package com.example.paymentservice.security;

import net.devh.boot.grpc.server.security.authentication.BearerAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.check.AccessPredicate;
import net.devh.boot.grpc.server.security.check.AccessPredicateVoter;
import net.devh.boot.grpc.server.security.check.GrpcSecurityMetadataSource;
import net.devh.boot.grpc.server.security.check.ManualGrpcSecurityMetadataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.util.List;

/**
 * Wires net.devh's gRPC authentication/authorization interceptors to stock
 * Spring Security OAuth2 resource-server components, so every RPC requires a
 * valid Keycloak-issued JWT carrying the {@code customer} or {@code admin}
 * realm role.
 *
 * <p>Only defining an {@link AuthenticationManager} + {@link GrpcAuthenticationReader}
 * is not sufficient on its own: net.devh's authenticating interceptor lets a
 * call with no {@code Authorization} header through unauthenticated (it only
 * rejects a call that presents an invalid token). The
 * {@link GrpcSecurityMetadataSource} + {@link AccessDecisionManager} beans
 * below are what actually enforce "every call must be authenticated".
 */
@Configuration
public class GrpcSecurityConfig {

    @Bean
    GrpcAuthenticationReader grpcAuthenticationReader() {
        return new BearerAuthenticationReader(BearerTokenAuthenticationToken::new);
    }

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

    @Bean
    AuthenticationManager authenticationManager(
            JwtDecoder jwtDecoder, Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter);
        return new ProviderManager(provider);
    }

    // AccessPredicate.hasAnyRole() does an *exact* match against getAuthority()
    // with no "ROLE_" auto-prefixing (unlike Spring Security's HttpSecurity
    // hasRole()). Authorities here are ROLE_-prefixed (KeycloakRealmRoleConverter),
    // so hasAnyAuthority() with the full authority strings is what's needed.
    @Bean
    GrpcSecurityMetadataSource grpcSecurityMetadataSource() {
        ManualGrpcSecurityMetadataSource source = new ManualGrpcSecurityMetadataSource();
        source.setDefault(AccessPredicate.hasAnyAuthority(
                new SimpleGrantedAuthority("ROLE_customer"), new SimpleGrantedAuthority("ROLE_admin")));
        return source;
    }

    // AccessDecisionManager/AccessDecisionVoter are deprecated in favor of
    // Spring Security 6's AuthorizationManager, but net.devh:grpc-server-spring-boot-starter
    // 3.1.0.RELEASE's AuthorizationCheckingServerInterceptor is still built
    // against this older API, so it's what's needed to activate that interceptor.
    @SuppressWarnings("deprecation")
    @Bean
    AccessDecisionManager accessDecisionManager() {
        List<AccessDecisionVoter<?>> voters = List.of(new AccessPredicateVoter());
        return new AffirmativeBased(voters);
    }
}
