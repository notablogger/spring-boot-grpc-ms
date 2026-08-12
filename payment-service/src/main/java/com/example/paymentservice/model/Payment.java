package com.example.paymentservice.model;

import com.example.grpc.payment.v1.PaymentStatus;

import java.math.BigDecimal;

public record Payment(
        String orderId,
        String paymentId,
        PaymentStatus status,
        BigDecimal amount,
        String currency
) {
}
