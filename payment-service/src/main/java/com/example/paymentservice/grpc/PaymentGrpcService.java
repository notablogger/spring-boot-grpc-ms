package com.example.paymentservice.grpc;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;

/**
 * gRPC endpoint implementation for {@code payment.v1.PaymentService}, backed by
 * {@link PaymentRepository}. Consumed by order-service to check payment status
 * for an order id.
 */
@GrpcService
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcService.class);

    private final PaymentRepository paymentRepository;

    /**
     * Creates a new PaymentGrpcService with the given payment repository.
     *
     * @param paymentRepository the repository for looking up payments by order ID
     */
    public PaymentGrpcService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Handles incoming gRPC requests to check the payment status for an order.
     *
     * <p>This method implements the async gRPC pattern using StreamObserver:
     * <ul>
     *   <li>Validates the order ID is not blank</li>
     *   <li>Queries the repository for a matching payment record</li>
     *   <li>Returns a PaymentStatusResponse via onNext() if found</li>
     *   <li>Returns a NOT_FOUND error status if not found</li>
     *   <li>Always calls onCompleted() or onError() to terminate the stream</li>
     * </ul>
     *
     * @param request the gRPC request containing the order ID
     * @param responseObserver the stream observer for sending the response or error back to the client
     */
    @PreAuthorize("hasAnyRole('customer', 'admin')")
    @Override
    public void checkPaymentStatus(PaymentStatusRequest request, StreamObserver<PaymentStatusResponse> responseObserver) {
        String orderId = request.getOrderId();
        // Validate that order ID is not blank
        if (!StringUtils.hasText(orderId)) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("order_id must not be blank")
                    .asRuntimeException());
            return;
        }

        // Query repository for payment record by order ID
        var payment = paymentRepository.findByOrderId(orderId);
        if (payment.isEmpty()) {
            // No payment found for this order
            log.debug("No payment found for order id {}", orderId);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("No payment found for order id '%s'".formatted(orderId))
                    .asRuntimeException());
            return;
        }

        // Send successful response and complete the stream
        responseObserver.onNext(toResponse(payment.get()));
        responseObserver.onCompleted();
    }

    /**
     * Converts a Payment domain object into a gRPC PaymentStatusResponse.
     *
     * @param payment the payment domain object to convert
     * @return a PaymentStatusResponse protobuf message
     */
    private static PaymentStatusResponse toResponse(Payment payment) {
        // Convert domain Payment object to protobuf response message
        return PaymentStatusResponse.newBuilder()
                .setOrderId(payment.orderId())
                .setPaymentId(payment.paymentId())
                .setStatus(toProtoStatus(payment.status()))
                .setAmount(payment.amount().doubleValue())
                .setCurrency(payment.currency())
                .build();
    }

    /**
     * Converts a domain PaymentStatus enum to its protobuf equivalent.
     *
     * @param status the domain PaymentStatus value
     * @return the corresponding protobuf PaymentStatus value
     */
    private static com.example.grpc.payment.v1.PaymentStatus toProtoStatus(
            com.example.paymentservice.model.PaymentStatus status) {
        // Map domain enum to protobuf enum
        return switch (status) {
            case PENDING -> com.example.grpc.payment.v1.PaymentStatus.PENDING;
            case COMPLETED -> com.example.grpc.payment.v1.PaymentStatus.COMPLETED;
            case FAILED -> com.example.grpc.payment.v1.PaymentStatus.FAILED;
            case REFUNDED -> com.example.grpc.payment.v1.PaymentStatus.REFUNDED;
        };
    }
}
