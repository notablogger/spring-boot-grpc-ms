package com.example.paymentservice.repository;

import com.example.paymentservice.model.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(String orderId);
}
