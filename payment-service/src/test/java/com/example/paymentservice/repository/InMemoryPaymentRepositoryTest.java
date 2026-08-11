package com.example.paymentservice.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPaymentRepositoryTest {

    private final PaymentRepository repository = createRepository();

    @Test
    void findsExistingPaymentByOrderId() {
        var payment = repository.findByOrderId("ORD-1001");

        assertThat(payment).isPresent();
        assertThat(payment.get().paymentId()).isEqualTo("PAY-5001");
        assertThat(payment.get().status().name()).isEqualTo("COMPLETED");
    }

    @Test
    void returnsEmptyForUnknownOrderId() {
        assertThat(repository.findByOrderId("does-not-exist")).isEmpty();
    }

    private static PaymentRepository createRepository() {
        try {
            return new InMemoryPaymentRepository(new ObjectMapper(), new ClassPathResource("data/payments.json"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
