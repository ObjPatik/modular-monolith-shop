package edu.cit.verano.events;

import java.time.Instant;

/**
 * Domain event published when a product's remaining stock drops below the threshold.
 */
public record LowStockEvent(
    String productId,
    String productName,
    int remainingStock,
    int threshold,
    Instant timestamp
) {
    public LowStockEvent(String productId, String productName, int remainingStock, int threshold) {
        this(productId, productName, remainingStock, threshold, Instant.now());
    }
}

