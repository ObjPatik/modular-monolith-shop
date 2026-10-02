package edu.cit.verano.events;

import java.time.Instant;
import java.util.List;

/**
 * Domain event published when an order is cancelled and inventory is restocked.
 */
public record OrderCancelledEvent(
    Long orderId,
    List<OrderItemEventDto> items,
    Instant timestamp
) {
    public OrderCancelledEvent(Long orderId, List<OrderItemEventDto> items) {
        this(orderId, items, Instant.now());
    }
}

