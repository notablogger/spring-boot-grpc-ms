package com.example.paymentservice.web;

import com.example.paymentservice.grpc.PaymentProtoMapper;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.watch.PaymentWatchRegistry;
import com.example.paymentservice.web.dto.PaymentStatusView;
import com.example.paymentservice.web.dto.UpdatePaymentStatusRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-only REST endpoint that mutates a payment's status directly (no
 * gRPC hop needed -- same process as {@code PaymentGrpcService}) and pushes
 * the new value to any open {@code WatchPaymentStatus} gRPC streams via
 * {@link PaymentWatchRegistry}.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentStatusController {

    private final PaymentRepository paymentRepository;
    private final PaymentWatchRegistry watchRegistry;

    public PaymentStatusController(PaymentRepository paymentRepository, PaymentWatchRegistry watchRegistry) {
        this.paymentRepository = paymentRepository;
        this.watchRegistry = watchRegistry;
    }

    @PatchMapping("/{orderId}/status")
    public PaymentStatusView updateStatus(@PathVariable String orderId, @RequestBody UpdatePaymentStatusRequest request) {
        PaymentStatus newStatus = parseStatus(request.status());

        Payment updated = paymentRepository.updateStatus(orderId, newStatus)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No payment found for order id '%s'".formatted(orderId)));

        boolean terminal = newStatus != PaymentStatus.PENDING;
        watchRegistry.publish(orderId, PaymentProtoMapper.toResponse(updated), terminal);

        return toView(updated);
    }

    private static PaymentStatus parseStatus(String raw) {
        if (raw != null) {
            try {
                return PaymentStatus.valueOf(raw.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to the exception below
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "status must be one of PENDING, COMPLETED, FAILED, REFUNDED");
    }

    private static PaymentStatusView toView(Payment payment) {
        return new PaymentStatusView(
                payment.orderId(), payment.paymentId(), payment.status().name(), payment.amount(), payment.currency());
    }
}
