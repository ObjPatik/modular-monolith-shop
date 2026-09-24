package edu.cit.verano.events;

import java.time.Instant;
import java.util.List;

/**
 * Domain event published when an order is successfully placed and confirmed.
 */
public record OrderPlacedEvent(
    Long orderId,
    List<OrderItemEventDto> items,
    Instant timestamp
) {
    public OrderPlacedEvent(Long orderId, List<OrderItemEventDto> items) {
        this(orderId, items, Instant.now());
    }
}

