package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * Loads sample order records from a bundled JSON fixture and serves them
 * from memory. Stands in for a real persistence layer (e.g. JPA repository)
 * for the purposes of this learning project.
 *
 * <p>Builds its own classic-Jackson-2 {@link ObjectMapper} rather than having
 * Spring inject one: Spring Boot 4's webmvc autoconfiguration defaults to
 * Jackson 3 ({@code tools.jackson.databind.ObjectMapper}, a different type),
 * so no classic-Jackson-2 {@code ObjectMapper} bean is available to inject
 * here once {@code spring-boot-starter-webmvc} is on the classpath.
 */
@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> ordersByOrderId;

    public InMemoryOrderRepository(@Value("classpath:data/orders.json") Resource ordersFixture) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        try (InputStream inputStream = ordersFixture.getInputStream()) {
            List<Order> orders = objectMapper.readValue(inputStream, new TypeReference<List<Order>>() {
            });
            this.ordersByOrderId = orders.stream()
                    .collect(Collectors.toUnmodifiableMap(Order::orderId, Function.identity()));
        }
        Assert.notEmpty(ordersByOrderId, "Order sample data must not be empty");
    }

    @Override
    public Optional<Order> findByOrderId(String orderId) {
        return Optional.ofNullable(ordersByOrderId.get(orderId));
    }
}
