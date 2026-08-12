package com.example.paymentservice.grpc;

import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.paymentservice.model.Payment;

/**
 * Maps {@link Payment} to the {@link PaymentStatusResponse} wire type used
 * by {@link PaymentGrpcService}'s two RPCs. payment-service's REST admin API
 * ({@code PaymentStatusController}) has its own separate DTO and doesn't use
 * this -- it never needs the proto shape.
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
