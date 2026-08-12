package com.example.paymentservice.repository;

import com.example.grpc.payment.v1.PaymentStatus;
import com.example.paymentservice.model.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(String orderId);

    /**
     * Updates the status of an existing payment. Returns the updated
     * payment, or empty if no payment exists for the given order id.
     */
    Optional<Payment> updateStatus(String orderId, PaymentStatus newStatus);
}
