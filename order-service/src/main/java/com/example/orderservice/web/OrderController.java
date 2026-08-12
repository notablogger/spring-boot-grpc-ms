package com.example.orderservice.web;

import com.example.grpc.payment.v1.PaymentStatus;
import com.example.orderservice.service.OrderPaymentStatusService;
import com.example.orderservice.watch.PaymentStatusWatchService;
import com.example.orderservice.web.dto.PaymentStatusView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderPaymentStatusService orderPaymentStatusService;
    private final PaymentStatusWatchService paymentStatusWatchService;

    public OrderController(
            OrderPaymentStatusService orderPaymentStatusService, PaymentStatusWatchService paymentStatusWatchService) {
        this.orderPaymentStatusService = orderPaymentStatusService;
        this.paymentStatusWatchService = paymentStatusWatchService;
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

    /**
     * Starts watching an order's payment status via payment-service's
     * server-streaming WatchPaymentStatus RPC, which polls up to {@code
     * count} times, {@code intervalSeconds} apart -- both caller-specified,
     * defaulting to 10 and 10. The watch stops on its own once a terminal
     * status is observed (see {@link PaymentStatusWatchService}). Updates
     * are only logged and recorded internally -- there's no REST-facing way
     * to read them back. Admin-only (see {@code WebSecurityConfig}); the
     * stream runs in the background, so this returns immediately.
     */
    @PostMapping("/{orderId}/payment-status/watch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void watchPaymentStatus(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "10") int intervalSeconds,
            PaymentStatus targetStatus) {
        paymentStatusWatchService.watchAsync(orderId, count, intervalSeconds,targetStatus);
    }
}
