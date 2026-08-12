package com.example.orderservice.watch;

import com.example.grpc.payment.v1.PaymentStatusResponse;
import com.example.orderservice.client.PaymentStatusClient;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises cancellation directly against the service, with a hand-rolled
 * blocking {@link Iterator} standing in for the real blocking-stub stream.
 * This proves PaymentStatusWatchService's own bookkeeping -- which watch
 * counts as "active", and that cancelling one actually interrupts its
 * consuming thread -- without needing a real gRPC transport: grpc-java's own
 * cancel-by-interrupt behavior on the generated blocking iterator is its own
 * well-tested code, not ours.
 */
class PaymentStatusWatchServiceTest {

    private final ExecutorService watchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutdown() {
        watchExecutor.shutdownNow();
    }

    @Test
    void cancelInterruptsAnInFlightWatchAndClearsItsBookkeeping() throws InterruptedException {
        String orderId = "ORD-CANCEL-TEST";
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch watchStarted = new CountDownLatch(1);

        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(new Order(
                orderId, "cust-01", BigDecimal.TEN, "USD", Instant.now())));

        PaymentStatusClient paymentStatusClient = mock(PaymentStatusClient.class);
        when(paymentStatusClient.watchPaymentStatus(orderId, 5, 10)).thenReturn(new Iterator<>() {
            @Override
            public boolean hasNext() {
                watchStarted.countDown();
                try {
                    // Blocks "forever" -- exactly like a real blocking-stub
                    // stream iterator waiting on the next server message --
                    // until interrupted by Future.cancel(true).
                    Thread.sleep(Long.MAX_VALUE);
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interrupted.set(true);
                    throw Status.CANCELLED.withCause(e).asRuntimeException();
                }
            }

            @Override
            public PaymentStatusResponse next() {
                throw new NoSuchElementException();
            }
        });

        PaymentStatusWatchService service = new PaymentStatusWatchService(
                orderRepository, paymentStatusClient, new PaymentStatusEventStore(), watchExecutor);

        service.watchAsync(orderId, 5, 10);
        assertThat(watchStarted.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(service.cancel(orderId)).isTrue();
        awaitTrue(interrupted);

        // Bookkeeping was cleared by the cancel above: nothing left to cancel again.
        assertThat(service.cancel(orderId)).isFalse();
    }

    @Test
    void cancelReturnsFalseWhenNoWatchIsActive() {
        PaymentStatusWatchService service = new PaymentStatusWatchService(
                mock(OrderRepository.class), mock(PaymentStatusClient.class), new PaymentStatusEventStore(), watchExecutor);

        assertThat(service.cancel("ORD-NEVER-WATCHED")).isFalse();
    }

    private static void awaitTrue(AtomicBoolean flag) {
        for (int attempt = 0; attempt < 40; attempt++) {
            if (flag.get()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Watch thread was not interrupted within timeout");
    }
}
