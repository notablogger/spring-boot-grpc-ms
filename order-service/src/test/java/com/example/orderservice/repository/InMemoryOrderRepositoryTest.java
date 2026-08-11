package com.example.orderservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOrderRepositoryTest {

    private final OrderRepository repository = createRepository();

    @Test
    void findsExistingOrderByOrderId() {
        var order = repository.findByOrderId("ORD-1001");

        assertThat(order).isPresent();
        assertThat(order.get().customerId()).isEqualTo("cust-01");
    }

    @Test
    void returnsEmptyForUnknownOrderId() {
        assertThat(repository.findByOrderId("does-not-exist")).isEmpty();
    }

    private static OrderRepository createRepository() {
        try {
            return new InMemoryOrderRepository(new ClassPathResource("data/orders.json"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
