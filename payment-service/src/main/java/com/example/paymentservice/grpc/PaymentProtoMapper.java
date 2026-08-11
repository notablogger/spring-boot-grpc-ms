package com.example.paymentservice.grpc;

import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.paymentservice.model.Payment;

/**
 * Domain-to-wire mapping shared by {@link PaymentGrpcService} and
 * payment-service's REST admin API ({@code PaymentStatusController}) --
 * both need to build the same {@link PaymentStatusResponse} shape, the
 * latter to push it via {@code PaymentWatchRegistry}.
 */
public final class PaymentProtoMapper {

    private PaymentProtoMapper() {
    }

    public static PaymentStatusResponse toResponse(Payment payment) {
        return PaymentStatusResponse.newBuilder()
                .setOrderId(payment.orderId())
                .setPaymentId(payment.paymentId())
                .setStatus(toProtoStatus(payment.status()))
                .setAmount(payment.amount().doubleValue())
                .setCurrency(payment.currency())
                .build();
    }

    public static com.example.grpc.payment.v1.PaymentStatus toProtoStatus(
            com.example.paymentservice.model.PaymentStatus status) {
        return switch (status) {
            case PENDING -> com.example.grpc.payment.v1.PaymentStatus.PENDING;
            case COMPLETED -> com.example.grpc.payment.v1.PaymentStatus.COMPLETED;
            case FAILED -> com.example.grpc.payment.v1.PaymentStatus.FAILED;
            case REFUNDED -> com.example.grpc.payment.v1.PaymentStatus.REFUNDED;
        };
    }
}
