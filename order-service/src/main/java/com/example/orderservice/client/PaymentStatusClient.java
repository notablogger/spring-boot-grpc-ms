package com.example.orderservice.client;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.exception.PaymentNotFoundException;
import com.example.orderservice.exception.PaymentServiceUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the generated {@link PaymentServiceGrpc} blocking stub that
 * translates gRPC status codes into order-service domain exceptions.
 *
 * <p>Field injection is used here (rather than constructor injection) because
 * {@code grpc-client-spring-boot-starter} wires {@code @GrpcClient} stubs via a
 * {@code BeanPostProcessor} that runs after construction.
 */
@Component
public class PaymentStatusClient {

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    /**
     * Checks the payment status for an order by making a synchronous gRPC call to payment-service.
     *
     * @param orderId the order ID to check payment status for
     * @return the payment status response containing payment details and status
     * @throws PaymentNotFoundException if no payment record exists for the given order ID
     * @throws PaymentServiceUnavailableException if the gRPC call fails for any reason other than NOT_FOUND
     */
    public PaymentStatusResponse checkPaymentStatus(String orderId) {
        try {
            // Build the gRPC request with the order ID
            return paymentServiceStub.checkPaymentStatus(
                    PaymentStatusRequest.newBuilder()
                            .setOrderId(orderId)
                            .build());
        } catch (StatusRuntimeException e) {
            // Translate NOT_FOUND status into domain exception for REST error handling
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new PaymentNotFoundException(orderId, e);
            }
            // All other errors indicate payment-service is unreachable or failed
            throw new PaymentServiceUnavailableException(
                    "payment-service call failed for order id '%s'".formatted(orderId), e);
        }
    }
}
