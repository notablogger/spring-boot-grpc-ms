package com.example.orderservice.model;

import java.time.Instant;

/**
 * A single status observation received from payment-service's
 * WatchPaymentStatus stream.
 */
public record PaymentStatusEvent(String orderId, String paymentId, String status, Instant observedAt) {
}
