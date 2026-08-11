package com.example.orderservice.service;

import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.client.PaymentStatusClient;
import com.example.orderservice.exception.OrderAccessDeniedException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.security.KeycloakRealmRoleConverter;
import com.example.orderservice.web.dto.PaymentStatusView;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Orchestrates a payment status check for an order: confirms the order exists
 * locally, enforces that the caller may view it, then delegates to
 * payment-service over gRPC for the current status.
 */
@Service
public class OrderPaymentStatusService {

    private static final String ADMIN_AUTHORITY = "ROLE_admin";

    private final OrderRepository orderRepository;
    private final PaymentStatusClient paymentStatusClient;
    private final KeycloakRealmRoleConverter keycloakRealmRoleConverter;

    public OrderPaymentStatusService(
            OrderRepository orderRepository,
            PaymentStatusClient paymentStatusClient,
            KeycloakRealmRoleConverter keycloakRealmRoleConverter) {
        this.orderRepository = orderRepository;
        this.paymentStatusClient = paymentStatusClient;
        this.keycloakRealmRoleConverter = keycloakRealmRoleConverter;
    }

    public PaymentStatusView getPaymentStatus(String orderId, Jwt callerToken) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!canView(order, callerToken)) {
            throw new OrderAccessDeniedException(orderId);
        }

        PaymentStatusResponse response = paymentStatusClient.checkPaymentStatus(orderId);
        return toView(response);
    }

    /**
     * admin may view any order; customer (WebSecurityConfig guarantees the
     * caller holds at least one of the two roles) may only view their own.
     */
    private boolean canView(Order order, Jwt callerToken) {
        boolean isAdmin = keycloakRealmRoleConverter.convert(callerToken).stream()
                .anyMatch(authority -> authority.getAuthority().equals(ADMIN_AUTHORITY));
        if (isAdmin) {
            return true;
        }
        String callerUsername = callerToken.getClaimAsString("preferred_username");
        return order.customerId().equalsIgnoreCase(callerUsername);
    }

    private static PaymentStatusView toView(PaymentStatusResponse response) {
        return new PaymentStatusView(
                response.getOrderId(),
                response.getPaymentId(),
                response.getStatus().name(),
                BigDecimal.valueOf(response.getAmount()),
                response.getCurrency());
    }
}
