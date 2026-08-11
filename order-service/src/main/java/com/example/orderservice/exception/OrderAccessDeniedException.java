package com.example.orderservice.exception;

public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException(String orderId) {
        super("Not permitted to view payment status for order id '%s'".formatted(orderId));
    }
}
