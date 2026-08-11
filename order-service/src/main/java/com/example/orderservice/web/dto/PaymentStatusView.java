package com.example.orderservice.web.dto;

import java.math.BigDecimal;

/**
 * REST representation of a payment status check result, returned by
 * {@code GET /api/v1/orders/{orderId}/payment-status}.
 */
public record PaymentStatusView(
        String orderId,
        String paymentId,
        String status,
        BigDecimal amount,
        String currency
) {
}
