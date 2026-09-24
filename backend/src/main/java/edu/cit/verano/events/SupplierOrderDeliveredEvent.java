package edu.cit.verano.events;

import java.time.Instant;

/**
 * Domain event published when an external purchase order has been delivered by the supplier.
 * Consumed by the Inventory module to restock available inventory, maintaining modular boundaries.
 */
public record SupplierOrderDeliveredEvent(
    String productId,
    int unitsRestocked,
    String poNumber,
    Instant timestamp
) {
    public SupplierOrderDeliveredEvent(String productId, int unitsRestocked, String poNumber) {
        this(productId, unitsRestocked, poNumber, Instant.now());
    }
}
