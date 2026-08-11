package com.example.paymentservice.security;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Exercises the real, fully-autoconfigured security interceptor chain (rather
 * than hand-assembling {@code ExceptionTranslatingServerInterceptor} /
 * {@code DefaultAuthenticatingServerInterceptor} / {@code ProviderManager}
 * ourselves, which could silently drift from what {@link GrpcSecurityConfig}
 * actually wires): a real Spring context binds its gRPC server in-process via
 * {@code grpc.server.in-process-name}, going through the same beans
 * production uses. Only {@link JwtDecoder} is mocked, since real token
 * signing/verification is Spring Security's own well-tested code, not ours;
 * everything downstream of {@code decode()} (claims-to-authority mapping,
 * authentication, authorization) is real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentServiceSecurityIntegrationTest {

    private static final String IN_PROCESS_SERVER_NAME = "payment-service-security-test";

    @MockBean
    private JwtDecoder jwtDecoder;

    private ManagedChannel channel;

    @DynamicPropertySource
    static void grpcServerProperties(DynamicPropertyRegistry registry) {
        registry.add("grpc.server.in-process-name", () -> IN_PROCESS_SERVER_NAME);
        registry.add("grpc.server.port", () -> -1);
    }

    @AfterEach
    void shutdownChannel() {
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    @Test
    void rejectsCallWithNoToken() {
        PaymentServiceGrpc.PaymentServiceBlockingStub stub = stub();

        assertThatThrownBy(() -> stub.checkPaymentStatus(request("ORD-1001")))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void rejectsCallWithInvalidToken() {
        when(jwtDecoder.decode("garbage")).thenThrow(new BadJwtException("bad token"));

        PaymentServiceGrpc.PaymentServiceBlockingStub stub = stubWithToken("garbage");

        assertThatThrownBy(() -> stub.checkPaymentStatus(request("ORD-1001")))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void rejectsValidTokenWithoutRequiredRole() {
        when(jwtDecoder.decode("no-roles-token")).thenReturn(validJwt("someone", "not-a-real-role"));

        PaymentServiceGrpc.PaymentServiceBlockingStub stub = stubWithToken("no-roles-token");

        assertThatThrownBy(() -> stub.checkPaymentStatus(request("ORD-1001")))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void acceptsCallWithValidCustomerToken() {
        when(jwtDecoder.decode("valid-token")).thenReturn(validJwt("cust-01", "customer"));

        PaymentServiceGrpc.PaymentServiceBlockingStub stub = stubWithToken("valid-token");
        var response = stub.checkPaymentStatus(request("ORD-1001"));

        assertThat(response.getOrderId()).isEqualTo("ORD-1001");
        assertThat(response.getPaymentId()).isEqualTo("PAY-5001");
    }

    private PaymentServiceGrpc.PaymentServiceBlockingStub stub() {
        channel = InProcessChannelBuilder.forName(IN_PROCESS_SERVER_NAME).directExecutor().build();
        return PaymentServiceGrpc.newBlockingStub(channel);
    }

    private PaymentServiceGrpc.PaymentServiceBlockingStub stubWithToken(String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return stub().withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static PaymentStatusRequest request(String orderId) {
        return PaymentStatusRequest.newBuilder().setOrderId(orderId).build();
    }

    private static Jwt validJwt(String username, String... roles) {
        return Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .subject(username)
                .claim("preferred_username", username)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
