package com.example.orderservice.watch;

import com.example.orderservice.model.PaymentStatusEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory record of payment-status events observed via
 * {@link PaymentStatusWatchService} -- just somewhere to "save" them for
 * this learning project (receive, save, and log; no REST exposure). Not
 * persisted across restarts.
 */
@Component
public class PaymentStatusEventStore {

    private final Map<String, List<PaymentStatusEvent>> eventsByOrderId = new ConcurrentHashMap<>();

    public void record(PaymentStatusEvent event) {
        eventsByOrderId.computeIfAbsent(event.orderId(), key -> new CopyOnWriteArrayList<>()).add(event);
    }

    public List<PaymentStatusEvent> eventsFor(String orderId) {
        return eventsByOrderId.getOrDefault(orderId, List.of());
    }
}
