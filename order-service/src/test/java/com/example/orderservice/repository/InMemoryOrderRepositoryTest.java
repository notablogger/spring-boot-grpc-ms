package com.example.orderservice.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrderRepositoryTest {

    private final OrderRepository repository = createRepository();

    @Test
    void findsExistingOrderByOrderId() {
        var order = repository.findByOrderId("ORD-1001");

        assertThat(order).isPresent();
        assertThat(order.get().customerId()).isEqualTo("CUST-01");
    }

    @Test
    void returnsEmptyForUnknownOrderId() {
        assertThat(repository.findByOrderId("does-not-exist")).isEmpty();
    }

    private static OrderRepository createRepository() {
        try {
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
            return new InMemoryOrderRepository(objectMapper, new ClassPathResource("data/orders.json"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
