package com.example.orderservice.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String orderId, Throwable cause) {
        super("No payment found for order id '%s'".formatted(orderId), cause);
    }
}
