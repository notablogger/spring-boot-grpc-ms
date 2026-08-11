package com.example.orderservice.web;

import com.example.orderservice.service.OrderPaymentStatusService;
import com.example.orderservice.web.dto.PaymentStatusView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderPaymentStatusService orderPaymentStatusService;

    public OrderController(OrderPaymentStatusService orderPaymentStatusService) {
        this.orderPaymentStatusService = orderPaymentStatusService;
    }

    /**
     * Triggers a payment status check for the given order id by calling
     * payment-service over gRPC. Requires a Keycloak-issued bearer token
     * (customer may only check their own orders; admin may check any).
     */
    @GetMapping("/{orderId}/payment-status")
    public PaymentStatusView getPaymentStatus(@PathVariable String orderId, @AuthenticationPrincipal Jwt callerToken) {
        return orderPaymentStatusService.getPaymentStatus(orderId, callerToken);
    }
}
