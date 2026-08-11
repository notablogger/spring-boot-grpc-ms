package com.example.paymentservice.web.dto;

import java.math.BigDecimal;

/**
 * REST representation of a payment, returned by
 * {@code PATCH /api/v1/payments/{orderId}/status}.
 */
public record PaymentStatusView(String orderId, String paymentId, String status, BigDecimal amount, String currency) {
}
