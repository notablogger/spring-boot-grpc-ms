package com.example.orderservice;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatus;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of order-service's REST layer against a real Spring context.
 * payment-service is replaced with an in-process gRPC server serving canned
 * responses, so no external process is required — this runs as part of the
 * regular {@code ./gradlew test} task and the GitHub Actions pipeline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderPaymentStatusIntegrationTest {

    private static final String IN_PROCESS_SERVER_NAME = "payment-service-test";

    private static Server fakePaymentServiceServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void grpcClientProperties(DynamicPropertyRegistry registry) {
        registry.add("grpc.client.payment-service.address", () -> "in-process:" + IN_PROCESS_SERVER_NAME);
    }

    @BeforeAll
    static void startFakePaymentService() throws IOException {
        fakePaymentServiceServer = InProcessServerBuilder.forName(IN_PROCESS_SERVER_NAME)
                .directExecutor()
                .addService(new FakePaymentService())
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
    void returnsPaymentStatusForKnownOrder() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orders/ORD-1001/payment-status", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"orderId\":\"ORD-1001\"");
        assertThat(response.getBody()).contains("\"paymentId\":\"PAY-5001\"");
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");
    }

    @Test
    void returns404WhenOrderDoesNotExistLocally() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orders/UNKNOWN-ORDER/payment-status", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returns404WhenPaymentRecordIsMissingUpstream() {
        // ORD-1005 exists in orders.json but the fake payment-service has no matching record for it.
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orders/ORD-1005/payment-status", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

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
