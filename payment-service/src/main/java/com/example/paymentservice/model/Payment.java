package com.example.paymentservice.model;

import java.math.BigDecimal;

public record Payment(
        String orderId,
        String paymentId,
        PaymentStatus status,
        BigDecimal amount,
        String currency
) {
}
