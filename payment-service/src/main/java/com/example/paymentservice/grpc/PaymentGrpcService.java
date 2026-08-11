package com.example.paymentservice.grpc;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.watch.PaymentWatchRegistry;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
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
    private final PaymentWatchRegistry watchRegistry;

    public PaymentGrpcService(PaymentRepository paymentRepository, PaymentWatchRegistry watchRegistry) {
        this.paymentRepository = paymentRepository;
        this.watchRegistry = watchRegistry;
    }

    @PreAuthorize("hasAnyRole('customer', 'admin')")
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

        responseObserver.onNext(PaymentProtoMapper.toResponse(payment.get()));
        responseObserver.onCompleted();
    }

    /**
     * Pushes the current status immediately, then -- for a payment still
     * {@code PENDING} -- registers with {@link PaymentWatchRegistry} to
     * receive any further update pushed by payment-service's REST admin API
     * ({@code PaymentStatusController}), and unregisters if the client
     * cancels the stream first.
     */
    @PreAuthorize("hasAnyRole('customer', 'admin')")
    @Override
    public void watchPaymentStatus(PaymentStatusRequest request, StreamObserver<PaymentStatusResponse> responseObserver) {
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

        Payment current = payment.get();
        responseObserver.onNext(PaymentProtoMapper.toResponse(current));

        if (current.status() != com.example.paymentservice.model.PaymentStatus.PENDING) {
            responseObserver.onCompleted();
            return;
        }

        watchRegistry.subscribe(orderId, responseObserver);
        if (responseObserver instanceof ServerCallStreamObserver<PaymentStatusResponse> serverCallStreamObserver) {
            serverCallStreamObserver.setOnCancelHandler(() -> watchRegistry.unsubscribe(orderId, responseObserver));
        }
    }
}
