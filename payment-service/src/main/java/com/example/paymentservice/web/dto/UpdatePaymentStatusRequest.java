package com.example.paymentservice.web.dto;

/**
 * Request body for {@code PATCH /api/v1/payments/{orderId}/status}.
 * {@code status} is matched case-insensitively against
 * {@link com.example.paymentservice.model.PaymentStatus}.
 */
public record UpdatePaymentStatusRequest(String status) {
}
