package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads sample payment records from a bundled JSON fixture into a mutable,
 * in-memory store -- stands in for a real persistence layer (e.g. a JPA
 * repository backed by a database) for the purposes of this learning
 * project. Mutable (unlike order-service's read-only InMemoryOrderRepository)
 * because UpdatePaymentStatus needs somewhere to write to.
 */
@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, Payment> paymentsByOrderId;

    public InMemoryPaymentRepository(
            ObjectMapper objectMapper,
            @Value("classpath:data/payments.json") Resource paymentsFixture
    ) throws IOException {
        try (InputStream inputStream = paymentsFixture.getInputStream()) {
            List<Payment> payments = objectMapper.readValue(inputStream, new TypeReference<List<Payment>>() {
            });
            this.paymentsByOrderId = new ConcurrentHashMap<>(payments.stream()
                    .collect(Collectors.toMap(Payment::orderId, Function.identity())));
        }
        Assert.notEmpty(paymentsByOrderId, "Payment sample data must not be empty");
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return Optional.ofNullable(paymentsByOrderId.get(orderId));
    }

    @Override
    public Optional<Payment> updateStatus(String orderId, PaymentStatus newStatus) {
        return Optional.ofNullable(paymentsByOrderId.computeIfPresent(orderId, (id, existing) ->
                new Payment(existing.orderId(), existing.paymentId(), newStatus, existing.amount(), existing.currency())));
    }
}
