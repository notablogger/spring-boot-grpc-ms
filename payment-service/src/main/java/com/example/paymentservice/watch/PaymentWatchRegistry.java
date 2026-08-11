package com.example.paymentservice.watch;

import com.example.grpc.payment.v1.PaymentStatusResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks open {@code WatchPaymentStatus} subscribers per order id, so
 * {@code UpdatePaymentStatus} can push a new status to anyone currently
 * watching. This is push-to-subscribers, not a durable event log: an update
 * only reaches watchers whose stream was already open when it happened.
 */
@Component
public class PaymentWatchRegistry {

    private static final Logger log = LoggerFactory.getLogger(PaymentWatchRegistry.class);

    private final Map<String, List<StreamObserver<PaymentStatusResponse>>> subscribersByOrderId = new ConcurrentHashMap<>();

    public void subscribe(String orderId, StreamObserver<PaymentStatusResponse> observer) {
        subscribersByOrderId.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>()).add(observer);
    }

    public void unsubscribe(String orderId, StreamObserver<PaymentStatusResponse> observer) {
        List<StreamObserver<PaymentStatusResponse>> subscribers = subscribersByOrderId.get(orderId);
        if (subscribers != null) {
            subscribers.remove(observer);
        }
    }

    /**
     * Pushes {@code update} to every currently-subscribed watcher of
     * {@code orderId}. If {@code terminal} is true (the new status is no
     * longer {@code PENDING}), each subscriber's stream is completed after
     * the push and the order id's subscriber list is cleared.
     */
    public void publish(String orderId, PaymentStatusResponse update, boolean terminal) {
        List<StreamObserver<PaymentStatusResponse>> subscribers = subscribersByOrderId.get(orderId);
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No active watchers for order {}; update not pushed anywhere", orderId);
            return;
        }
        for (StreamObserver<PaymentStatusResponse> subscriber : subscribers) {
            subscriber.onNext(update);
            if (terminal) {
                subscriber.onCompleted();
            }
        }
        if (terminal) {
            subscribersByOrderId.remove(orderId);
        }
    }
}
