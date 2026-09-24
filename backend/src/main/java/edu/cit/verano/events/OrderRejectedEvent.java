package edu.cit.verano.events;

import java.time.Instant;
import java.util.List;

/**
 * Domain event published when an order fails validation or is rejected.
 */
public record OrderRejectedEvent(
    Long orderId,
    String reason,
    List<OrderItemEventDto> items,
    Instant timestamp
) {
    public OrderRejectedEvent(Long orderId, String reason, List<OrderItemEventDto> items) {
        this(orderId, reason, items, Instant.now());
    }
}

