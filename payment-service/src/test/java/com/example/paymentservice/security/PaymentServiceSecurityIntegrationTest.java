package com.example.paymentservice.security;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatus;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Exercises the real, fully-autoconfigured security interceptor chains for
 * both transports (rather than hand-assembling them, which could silently
 * drift from what {@link GrpcSecurityConfig}/{@link WebSecurityConfig} plus
 * {@code @PreAuthorize} on {@code PaymentGrpcService} actually wire): a real
 * Spring context binds its gRPC server in-process via
 * {@code spring.grpc.server.inprocess.name} and its REST API on a random
 * port, going through the same beans production uses. Only {@link JwtDecoder}
 * is mocked (shared by both transports), since real token signing/verification
 * is Spring Security's own well-tested code, not ours; everything downstream
 * of {@code decode()} (claims-to-authority mapping, authentication,
 * authorization) is real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PaymentServiceSecurityIntegrationTest {

    private static final String IN_PROCESS_SERVER_NAME = "payment-service-security-test";

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TestRestTemplate restTemplate;

    private ManagedChannel channel;

    @DynamicPropertySource
    static void grpcServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.server.inprocess.name", () -> IN_PROCESS_SERVER_NAME);
        registry.add("spring.grpc.server.port", () -> -1);
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

    @Test
    void watchRejectsCallWithNoToken() {
        PaymentServiceGrpc.PaymentServiceBlockingStub stub = stub();
        Iterator<PaymentStatusResponse> updates = stub.watchPaymentStatus(request("ORD-1001"));

        assertThatThrownBy(updates::hasNext)
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void watchEmitsSingleUpdateForAlreadySettledPayment() {
        // ORD-1001 is COMPLETED in payments.json -- already terminal, so the
        // stream should close after exactly one update.
        when(jwtDecoder.decode("valid-token")).thenReturn(validJwt("cust-01", "customer"));
        PaymentServiceGrpc.PaymentServiceBlockingStub stub = stubWithToken("valid-token");

        Iterator<PaymentStatusResponse> updates = stub.watchPaymentStatus(request("ORD-1001"));

        assertThat(updates.next().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(updates.hasNext()).isFalse();
    }

    @Test
    void watchEmitsSecondUpdateWhenRestApiChangesStatus() throws InterruptedException {
        // ORD-1002 is PENDING in payments.json. The real trigger for the
        // second update is the admin REST endpoint (PaymentStatusController)
        // pushing through PaymentWatchRegistry -- not a timer.
        when(jwtDecoder.decode("watch-token")).thenReturn(validJwt("cust-02", "customer"));
        when(jwtDecoder.decode("admin-token")).thenReturn(validJwt("some-admin", "admin"));

        PaymentServiceGrpc.PaymentServiceBlockingStub stub = stubWithToken("watch-token");
        Iterator<PaymentStatusResponse> updates = stub.watchPaymentStatus(request("ORD-1002"));

        assertThat(updates.next().getStatus()).isEqualTo(PaymentStatus.PENDING);

        // Fire the update from a separate thread: the main thread is about
        // to block on updates.next() waiting for exactly this push, so
        // issuing the REST call inline here would deadlock.
        Thread updater = new Thread(() -> patchStatusWithToken("ORD-1002", "completed", "admin-token"));
        updater.start();

        assertThat(updates.next().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(updates.hasNext()).isFalse();
        updater.join();
    }

    @Test
    void restUpdateChangesStatusAndReturnsUpdatedView() {
        when(jwtDecoder.decode("admin-token")).thenReturn(validJwt("some-admin", "admin"));

        ResponseEntity<String> response = patchStatusWithToken("ORD-1003", "refunded", "admin-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"REFUNDED\"");
    }

    @Test
    void restUpdateRequiresAdminRole() {
        when(jwtDecoder.decode("customer-token")).thenReturn(validJwt("cust-01", "customer"));

        ResponseEntity<String> response = patchStatusWithToken("ORD-1001", "completed", "customer-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void restUpdateRejectsCallWithNoToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/payments/{orderId}/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"completed\"}", headers), String.class, "ORD-1001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void restUpdateReturns404ForUnknownOrder() {
        when(jwtDecoder.decode("admin-token")).thenReturn(validJwt("some-admin", "admin"));

        ResponseEntity<String> response = patchStatusWithToken("ORD-9999", "completed", "admin-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void restUpdateReturns400ForInvalidStatus() {
        when(jwtDecoder.decode("admin-token")).thenReturn(validJwt("some-admin", "admin"));

        ResponseEntity<String> response = patchStatusWithToken("ORD-1001", "not-a-status", "admin-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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

    private ResponseEntity<String> patchStatusWithToken(String orderId, String status, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/v1/payments/{orderId}/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"%s\"}".formatted(status), headers), String.class, orderId);
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
