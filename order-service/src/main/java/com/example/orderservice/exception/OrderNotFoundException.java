package com.example.orderservice.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("No order found with id '%s'".formatted(orderId));
    }
}
