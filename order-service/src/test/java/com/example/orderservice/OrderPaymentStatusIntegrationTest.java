package com.example.orderservice;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatus;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.security.JwtRelayClientInterceptor;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of order-service's REST layer against a real Spring context.
 * payment-service is replaced with a plain (Spring-free) in-process gRPC
 * server serving canned responses. The client side is rewired via a
 * {@code @Primary} {@link GrpcChannelFactory} test bean rather than
 * {@code @AutoConfigureTestGrpcTransport}: that annotation's channel factory
 * doesn't apply {@code GrpcChannelBuilderCustomizer} beans, which would
 * silently drop {@link JwtRelayClientInterceptor} and defeat the one thing
 * this test most needs to prove. Keycloak is likewise replaced: the
 * resource-server JwtDecoder is swapped for a symmetric-key decoder
 * ({@link TestJwtDecoderConfig}) so tokens can be minted locally with a fixed
 * test secret, without needing a running IdP in CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class OrderPaymentStatusIntegrationTest {

    private static final String IN_PROCESS_SERVER_NAME = "payment-service-test";
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static Server fakePaymentServiceServer;
    private static final AtomicReference<String> lastAuthorizationHeader = new AtomicReference<>();

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startFakePaymentService() throws IOException {
        // Records whatever "authorization" header actually arrives on the
        // fake server, so relaysCallersBearerTokenToPaymentService() below
        // can assert JwtRelayClientInterceptor really attached the caller's
        // token rather than just trusting it compiled.
        ServerInterceptor authorizationCapturingInterceptor = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                lastAuthorizationHeader.set(headers.get(AUTHORIZATION_HEADER));
                return Contexts.interceptCall(Context.current(), call, headers, next);
            }
        };
        fakePaymentServiceServer = InProcessServerBuilder.forName(IN_PROCESS_SERVER_NAME)
                .directExecutor()
                .addService(ServerInterceptors.intercept(new FakePaymentService(), authorizationCapturingInterceptor))
                .build()
                .start();
    }

    @AfterAll
    static void stopFakePaymentService() {
        if (fakePaymentServiceServer != null) {
            fakePaymentServiceServer.shutdownNow();
        }
    }

    @Test
    void returns401WhenNoTokenProvided() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orders/ORD-1001/payment-status", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsPaymentStatusForOwnOrder() {
        // ORD-1001 belongs to customerId "cust-01" in orders.json
        ResponseEntity<String> response = getWithToken("/api/v1/orders/ORD-1001/payment-status", token("cust-01", "customer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"orderId\":\"ORD-1001\"");
        assertThat(response.getBody()).contains("\"paymentId\":\"PAY-5001\"");
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");
    }

    @Test
    void returns403WhenCustomerRequestsSomeoneElsesOrder() {
        // ORD-1001 belongs to "cust-01", not "cust-02"
        ResponseEntity<String> response = getWithToken("/api/v1/orders/ORD-1001/payment-status", token("cust-02", "customer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminMayViewAnyOrder() {
        ResponseEntity<String> response = getWithToken("/api/v1/orders/ORD-1001/payment-status", token("some-admin", "admin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void returns404WhenOrderDoesNotExistLocally() {
        ResponseEntity<String> response = getWithToken("/api/v1/orders/UNKNOWN-ORDER/payment-status", token("some-admin", "admin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenPaymentRecordIsMissingUpstream() {
        // ORD-1005 exists in orders.json but the fake payment-service has no matching record for it.
        ResponseEntity<String> response = getWithToken("/api/v1/orders/ORD-1005/payment-status", token("some-admin", "admin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void relaysCallersBearerTokenToPaymentService() {
        // The core token-relay guarantee: order-service must forward the
        // exact same token it validated, not mint or modify one of its own.
        String token = token("cust-01", "customer");

        getWithToken("/api/v1/orders/ORD-1001/payment-status", token);

        assertThat(lastAuthorizationHeader.get()).isEqualTo("Bearer " + token);
    }

    private ResponseEntity<String> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String token(String username, String... realmRoles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("preferred_username", username)
                    .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                    .build();
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(new MACSigner(TestJwtDecoderConfig.TEST_KEY_BYTES));
            return signedJwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to mint test JWT", e);
        }
    }

    // Overrides the production JwtDecoder (which points at a real Keycloak
    // issuer-uri) with a symmetric-key one, so token(...) below can mint
    // valid tokens locally with a fixed test secret instead of needing a
    // running IdP.
    @TestConfiguration
    static class TestJwtDecoderConfig {

        static final byte[] TEST_KEY_BYTES =
                "order-payment-status-integration-test-only-secret".getBytes(StandardCharsets.UTF_8);

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(TEST_KEY_BYTES, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        }
    }

    @TestConfiguration
    static class TestGrpcChannelConfig {

        // Routes every channel to the in-process fake server regardless of
        // the configured target, and -- unlike the framework's own test
        // channel factory -- applies JwtRelayClientInterceptor directly, so
        // the relay behaviour under test actually runs.
        @Bean
        @Primary
        GrpcChannelFactory testGrpcChannelFactory() {
            return new GrpcChannelFactory() {
                @Override
                public boolean supports(String target) {
                    return true;
                }

                @Override
                public ManagedChannel createChannel(String target, ChannelBuilderOptions options) {
                    return InProcessChannelBuilder.forName(IN_PROCESS_SERVER_NAME)
                            .directExecutor()
                            .intercept(new JwtRelayClientInterceptor())
                            .build();
                }
            };
        }
    }

    // Stands in for the real payment-service: only knows about ORD-1001 (with
    // the same PAY-5001/COMPLETED/129.99/USD values as the real
    // payments.json fixture, so returnsPaymentStatusForOwnOrder()'s
    // assertions stay meaningful), and NOT_FOUND for anything else --
    // including ORD-1005, which exercises the "order exists locally but
    // payment-service has no record" 404 case.
    private static final class FakePaymentService extends PaymentServiceGrpc.PaymentServiceImplBase {

        @Override
        public void checkPaymentStatus(
                PaymentStatusRequest request, StreamObserver<PaymentStatusResponse> responseObserver) {
            if ("ORD-1001".equals(request.getOrderId())) {
                responseObserver.onNext(PaymentStatusResponse.newBuilder()
                        .setOrderId("ORD-1001")
                        .setPaymentId("PAY-5001")
                        .setStatus(PaymentStatus.COMPLETED)
                        .setAmount(129.99)
                        .setCurrency("USD")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("No payment found for order id '%s'".formatted(request.getOrderId()))
                    .asRuntimeException());
        }
    }
}
