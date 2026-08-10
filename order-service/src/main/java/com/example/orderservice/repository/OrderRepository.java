package com.example.orderservice.repository;

import com.example.orderservice.model.Order;

import java.util.Optional;

public interface OrderRepository {

    Optional<Order> findByOrderId(String orderId);
}
