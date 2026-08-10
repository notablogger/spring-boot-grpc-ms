package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads sample payment records from a bundled JSON fixture and serves them
 * from memory. Stands in for a real persistence layer (e.g. JPA repository)
 * for the purposes of this learning project.
 */
@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, Payment> paymentsByOrderId;

    public InMemoryPaymentRepository(
            ObjectMapper objectMapper,
            @Value("classpath:data/payments.json") Resource paymentsFixture
    ) throws IOException {
        try (InputStream inputStream = paymentsFixture.getInputStream()) {
            List<Payment> payments = objectMapper.readValue(inputStream, new com.fasterxml.jackson.core.type.TypeReference<List<Payment>>() {
            });
            this.paymentsByOrderId = payments.stream()
                    .collect(Collectors.toUnmodifiableMap(Payment::orderId, Function.identity()));
        }
        Assert.notEmpty(paymentsByOrderId, "Payment sample data must not be empty");
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return Optional.ofNullable(paymentsByOrderId.get(orderId));
    }
}
