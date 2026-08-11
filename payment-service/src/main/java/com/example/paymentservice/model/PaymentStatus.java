package com.example.paymentservice.model;

/**
 * Domain representation of a payment's lifecycle state, independent of the
 * generated gRPC/protobuf enum so the wire contract can evolve separately
 * from the internal model.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
