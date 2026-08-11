package com.example.orderservice.exception;

public class WatchNotActiveException extends RuntimeException {

    public WatchNotActiveException(String orderId) {
        super("No active payment-status watch for order id '%s'".formatted(orderId));
    }
}
