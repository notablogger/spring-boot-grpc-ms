package com.example.orderservice.watch;

import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.client.PaymentStatusClient;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.PaymentStatusEvent;
import com.example.orderservice.repository.OrderRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Consumes payment-service's WatchPaymentStatus stream in the background:
 * every update is logged and recorded in {@link PaymentStatusEventStore}.
 * Nothing here is relayed back over REST -- this is purely an internal
 * observation feed, triggered by a dedicated admin-only endpoint
 * ({@code OrderController.watchPaymentStatus}), separate from the existing
 * payment-status check. A watch can also be stopped early via {@link
 * #cancel(String)} ({@code OrderController.cancelWatch}).
 */
@Service
public class PaymentStatusWatchService {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusWatchService.class);

    private final OrderRepository orderRepository;
    private final PaymentStatusClient paymentStatusClient;
    private final PaymentStatusEventStore eventStore;
    private final ExecutorService watchExecutor;

    // Tracks the in-flight watch for each order id so it can be cancelled by
    // orderId later. The blocking-stub stream iterator has no cancel() of
    // its own; interrupting the thread it's blocked in is grpc-java's own
    // documented way to unwind it (hasNext()/next() then throw a
    // StatusRuntimeException with Status.CANCELLED).
    private final Map<String, Future<?>> activeWatches = new ConcurrentHashMap<>();

    public PaymentStatusWatchService(
            OrderRepository orderRepository,
            PaymentStatusClient paymentStatusClient,
            PaymentStatusEventStore eventStore,
            ExecutorService watchExecutor) {
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
     * <p>
     * Replaces any watch already running for this order id, stopping the old
     * one first so it doesn't keep running unobserved.
     *
     * @param watchCount how many times payment-service should poll before
     *                   giving up on a payment that never settles
     * @param intervalSeconds how long payment-service waits between polls
     */
    public void watchAsync(String orderId, int watchCount, int intervalSeconds) {
        orderRepository.findByOrderId(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        cancel(orderId);

        SecurityContext callerContext = SecurityContextHolder.getContext();

        // A task can't reference the Future submit() is about to return for
        // it, so it reads its own identity out of this box, filled in right
        // after submit() returns -- needed so the finally block below only
        // ever removes its own map entry, never a newer watch's.
        AtomicReference<Future<?>> self = new AtomicReference<>();
        Future<?> future = watchExecutor.submit(() -> {
            SecurityContextHolder.setContext(callerContext);
            try {
                consume(orderId, watchCount, intervalSeconds);
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == Status.Code.CANCELLED) {
                    log.info("Watch for order {} cancelled", orderId);
                } else {
                    log.warn("Watch stream for order {} ended abnormally", orderId, e);
                }
            } catch (Exception e) {
                log.warn("Watch stream for order {} ended abnormally", orderId, e);
            } finally {
                SecurityContextHolder.clearContext();
                activeWatches.remove(orderId, self.get());
            }
        });
        self.set(future);
        activeWatches.put(orderId, future);
    }

    /**
     * Stops the watch running for an order id, if there is one.
     *
     * @return {@code true} if an active watch was found and cancelled,
     *         {@code false} if there wasn't one running
     */
    public boolean cancel(String orderId) {
        Future<?> future = activeWatches.remove(orderId);
        if (future == null) {
            return false;
        }
        future.cancel(true);
        return true;
    }

    private void consume(String orderId, int watchCount, int intervalSeconds) {
        Iterator<PaymentStatusResponse> updates = paymentStatusClient.watchPaymentStatus(orderId, watchCount, intervalSeconds);
        while (updates.hasNext()) {
            PaymentStatusResponse update = updates.next();
            log.info("payment status update: order={} paymentId={} status={}",
                    update.getOrderId(), update.getPaymentId(), update.getStatus());
            eventStore.record(new PaymentStatusEvent(
                    update.getOrderId(), update.getPaymentId(), update.getStatus().name(), Instant.now()));
        }
    }
}
