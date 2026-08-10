package com.example.orderservice.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt
) {
}
