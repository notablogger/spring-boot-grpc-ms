package com.example.orderservice.watch;

import com.example.grpc.payment.v1.PaymentStatus;
import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.client.PaymentStatusClient;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.PaymentStatusEvent;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.Executor;

/**
 * Consumes payment-service's WatchPaymentStatus stream in the background:
 * every update is logged and recorded in {@link PaymentStatusEventStore}.
 * Nothing here is relayed back over REST -- this is purely an internal
 * observation feed, triggered by a dedicated admin-only endpoint
 * ({@code OrderController.watchPaymentStatus}), separate from the existing
 * payment-status check. Consumption stops as soon as a terminal status is
 * observed, or once payment-service's own poll schedule runs out.
 */
@Service
public class PaymentStatusWatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusWatchService.class);

    private final OrderRepository orderRepository;
    private final PaymentStatusClient paymentStatusClient;
    private final PaymentStatusEventStore eventStore;
    private final Executor watchExecutor;

    public PaymentStatusWatchService(
            OrderRepository orderRepository,
            PaymentStatusClient paymentStatusClient,
            PaymentStatusEventStore eventStore,
            Executor watchExecutor) {
        this.orderRepository = orderRepository;
        this.paymentStatusClient = paymentStatusClient;
        this.eventStore = eventStore;
        this.watchExecutor = watchExecutor;
    }

    /**
     * Validates the order exists synchronously (so a bad order id fails the
     * request immediately rather than silently in the background), then
     * fires the watch off. The caller's {@link SecurityContext} is captured
     * up front and restored on the background thread, since
     * {@code JwtRelayClientInterceptor} reads it via the thread-local
     * {@link SecurityContextHolder}, which a fresh background thread
     * wouldn't otherwise have populated.
     *
     * @param watchCount      how many times payment-service should poll before
     *                        giving up on a payment that never settles
     * @param intervalSeconds how long payment-service waits between polls
     * @param targetStatus
     */
    public void watchAsync(String orderId, int watchCount, int intervalSeconds, PaymentStatus targetStatus) {
        orderRepository.findByOrderId(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

        SecurityContext callerContext = SecurityContextHolder.getContext();

        // This makes sure that the watch is executed in a separate thread(virtual threads),
        // so that the main thread can continue processing other requests without
        // being blocked by the watch operation. The SecurityContext is set for the background
        // thread to ensure that any security-related operations performed during the watch have
        // the correct context.
        watchExecutor.execute(() -> {
            SecurityContextHolder.setContext(callerContext);
            try {
                consume(orderId, watchCount, intervalSeconds,targetStatus);
            } catch (Exception e) {
                log.warn("Watch stream for order {} ended abnormally", orderId, e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private void consume(String orderId, int watchCount, int intervalSeconds, PaymentStatus targetStatus) {
        Iterator<PaymentStatusResponse> updates = paymentStatusClient.watchPaymentStatus(orderId, watchCount,
                intervalSeconds,targetStatus);
        while (updates.hasNext()) {
            PaymentStatusResponse update = updates.next();
            log.info("payment status update: order={} paymentId={} status={}",
                    update.getOrderId(), update.getPaymentId(), update.getStatus());
            //this is needed for tests to check if the status has indeed arrived from payment service
            eventStore.record(new PaymentStatusEvent(
                    update.getOrderId(), update.getPaymentId(), update.getStatus().name(), Instant.now()));

            if (update.getStatus() == targetStatus) {
                log.info("Ending watch for order {} finished: status={}", orderId, update.getStatus());
                break;
            }
        }
    }
}
