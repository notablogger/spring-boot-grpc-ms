package com.example.orderservice.web;

import com.example.orderservice.exception.WatchNotActiveException;
import com.example.orderservice.service.OrderPaymentStatusService;
import com.example.orderservice.watch.PaymentStatusWatchService;
import com.example.orderservice.web.dto.PaymentStatusView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * server-streaming WatchPaymentStatus RPC. Updates are only logged and
     * recorded internally (see {@link PaymentStatusWatchService}) -- there's
     * no REST-facing way to read them back. Admin-only (see
     * {@code WebSecurityConfig}); the stream runs in the background, so this
     * returns immediately.
     */
    @PostMapping("/{orderId}/payment-status/watch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void watchPaymentStatus(@PathVariable String orderId) {
        paymentStatusWatchService.watchAsync(orderId);
    }

    /**
     * Stops a watch started via {@link #watchPaymentStatus}, if one is still
     * running for the given order id. Admin-only, same as starting one.
     */
    @DeleteMapping("/{orderId}/payment-status/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelWatch(@PathVariable String orderId) {
        if (!paymentStatusWatchService.cancel(orderId)) {
            throw new WatchNotActiveException(orderId);
        }
    }
}
