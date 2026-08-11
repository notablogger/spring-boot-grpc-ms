package com.example.orderservice.client;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.exception.PaymentNotFoundException;
import com.example.orderservice.exception.PaymentServiceAuthenticationException;
import com.example.orderservice.exception.PaymentServiceUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around the generated {@link PaymentServiceGrpc} blocking stub that
 * translates gRPC status codes into order-service domain exceptions. The stub
 * itself is supplied as a bean by {@code @ImportGrpcClients} on
 * {@link com.example.orderservice.OrderServiceApplication}.
 */
@Component
public class PaymentStatusClient {

    private final PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    public PaymentStatusClient(PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub) {
        this.paymentServiceStub = paymentServiceStub;
    }

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
            Status.Code code = e.getStatus().getCode();
            // Translate NOT_FOUND status into domain exception for REST error handling
            if (code == Status.Code.NOT_FOUND) {
                throw new PaymentNotFoundException(orderId, e);
            }
            // payment-service rejected the relayed token; distinct from a plain
            // outage since retrying will not fix it (see exception javadoc)
            if (code == Status.Code.UNAUTHENTICATED || code == Status.Code.PERMISSION_DENIED) {
                throw new PaymentServiceAuthenticationException(
                        "payment-service rejected the relayed bearer token for order id '%s'".formatted(orderId), e);
            }
            // All other errors indicate payment-service is unreachable or failed
            throw new PaymentServiceUnavailableException(
                    "payment-service call failed for order id '%s'".formatted(orderId), e);
        }
    }
}
