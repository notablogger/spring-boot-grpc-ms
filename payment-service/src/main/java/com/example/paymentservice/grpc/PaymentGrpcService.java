package com.example.paymentservice.grpc;

import com.example.grpc.payment.v1.PaymentServiceGrpc;
import com.example.grpc.payment.v1.PaymentStatusRequest;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

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
     * Polls the current status up to {@code watch_count} times, waiting
     * {@code watch_interval_seconds} between pushes, closing the stream
     * early once the payment reaches a terminal state. There is no shared
     * subscriber registry: each call independently re-reads {@link
     * PaymentRepository} on every iteration, so a change made via the REST
     * admin API ({@code PaymentStatusController}) becomes visible on the
     * next poll rather than being pushed live. The caller (order-service)
     * chooses the schedule via the request.
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

        int watchCount = request.getWatchCount();
        int intervalSeconds = request.getWatchIntervalSeconds();
        if (watchCount < 1 || intervalSeconds < 1) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("watch_count and watch_interval_seconds must both be positive")
                    .asRuntimeException());
            return;
        }

        for (int attempt = 0; attempt < watchCount; attempt++) {
            Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
            if (payment.isEmpty()) {
                log.debug("No payment found for order id {}", orderId);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No payment found for order id '%s'".formatted(orderId))
                        .asRuntimeException());
                return;
            }

            Payment current = payment.get();
            responseObserver.onNext(PaymentProtoMapper.toResponse(current));

            if (current.status() != PaymentStatus.PENDING) {
                responseObserver.onCompleted();
                return;
            }

            boolean lastAttempt = attempt == watchCount - 1;
            if (!lastAttempt) {
                try {
                    Thread.sleep(Duration.ofSeconds(intervalSeconds));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Watch for order {} interrupted while waiting for next poll", orderId);
                    return;
                }
            }
        }

        // Still PENDING after watch_count polls -- give up rather than wait forever.
        responseObserver.onCompleted();
    }
}
