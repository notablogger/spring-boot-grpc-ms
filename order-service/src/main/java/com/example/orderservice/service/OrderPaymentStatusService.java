package com.example.orderservice.service;

import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.client.PaymentStatusClient;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.web.dto.PaymentStatusView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Orchestrates a payment status check for an order: confirms the order exists
 * locally, then delegates to payment-service over gRPC for the current status.
 */
@Service
public class OrderPaymentStatusService {

    private final OrderRepository orderRepository;
    private final PaymentStatusClient paymentStatusClient;

    public OrderPaymentStatusService(OrderRepository orderRepository, PaymentStatusClient paymentStatusClient) {
        this.orderRepository = orderRepository;
        this.paymentStatusClient = paymentStatusClient;
    }

    public PaymentStatusView getPaymentStatus(String orderId) {
        orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        PaymentStatusResponse response = paymentStatusClient.checkPaymentStatus(orderId);
        return toView(response);
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
