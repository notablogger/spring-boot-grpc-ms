package com.example.orderservice.exception;

/**
 * Thrown when payment-service rejects the relayed bearer token
 * (UNAUTHENTICATED/PERMISSION_DENIED). This should not happen in normal
 * operation since order-service only relays tokens its own resource-server
 * layer already validated; if it does, it points at a persistent
 * misconfiguration (e.g. the two services pointed at different Keycloak
 * realms), not a transient outage.
 */
public class PaymentServiceAuthenticationException extends RuntimeException {

    public PaymentServiceAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
