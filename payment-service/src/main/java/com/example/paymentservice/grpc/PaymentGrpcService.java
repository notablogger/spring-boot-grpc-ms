package com.example.paymentservice.grpc;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public PaymentGrpcService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public void checkPaymentStatus(PaymentStatusRequest request, StreamObserver<PaymentStatusResponse> responseObserver) {
        String orderId = request.getOrderId();
        if (!StringUtils.hasText(orderId)) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("order_id must not be blank")
                    .asRuntimeException());
            return;
        }

        var payment = paymentRepository.findByOrderId(orderId);
        if (payment.isEmpty()) {
            log.debug("No payment found for order id {}", orderId);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("No payment found for order id '%s'".formatted(orderId))
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(toResponse(payment.get()));
        responseObserver.onCompleted();
    }

    private static PaymentStatusResponse toResponse(Payment payment) {
        return PaymentStatusResponse.newBuilder()
                .setOrderId(payment.orderId())
                .setPaymentId(payment.paymentId())
                .setStatus(toProtoStatus(payment.status()))
                .setAmount(payment.amount().doubleValue())
                .setCurrency(payment.currency())
                .build();
    }

    private static com.example.grpc.payment.v1.PaymentStatus toProtoStatus(
            com.example.paymentservice.model.PaymentStatus status) {
        return switch (status) {
            case PENDING -> com.example.grpc.payment.v1.PaymentStatus.PENDING;
            case COMPLETED -> com.example.grpc.payment.v1.PaymentStatus.COMPLETED;
            case FAILED -> com.example.grpc.payment.v1.PaymentStatus.FAILED;
            case REFUNDED -> com.example.grpc.payment.v1.PaymentStatus.REFUNDED;
        };
    }
}
