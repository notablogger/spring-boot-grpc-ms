package com.example.orderservice.web;

import com.example.orderservice.exception.OrderAccessDeniedException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.exception.PaymentNotFoundException;
import com.example.orderservice.exception.PaymentServiceAuthenticationException;
import com.example.orderservice.exception.PaymentServiceUnavailableException;
import com.example.orderservice.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(PaymentServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePaymentServiceUnavailable(
            PaymentServiceUnavailableException ex, HttpServletRequest request) {
        log.warn("payment-service call failed: {}", ex.getMessage(), ex);
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex, request);
    }

    @ExceptionHandler(OrderAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleOrderAccessDenied(OrderAccessDeniedException ex, HttpServletRequest request) {
        return errorResponse(HttpStatus.FORBIDDEN, ex, request);
    }

    @ExceptionHandler(PaymentServiceAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handlePaymentServiceAuthentication(
            PaymentServiceAuthenticationException ex, HttpServletRequest request) {
        // A persistent misconfiguration (e.g. mismatched Keycloak realms), not a
        // transient blip, so it's logged at error (not warn) and kept distinct
        // from the 503 mapping above: retrying will not fix this.
        log.error("payment-service rejected our relayed token: {}", ex.getMessage(), ex);
        return errorResponse(HttpStatus.BAD_GATEWAY, ex, request);
    }

    private static ResponseEntity<ErrorResponse> errorResponse(
            HttpStatus status, Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
