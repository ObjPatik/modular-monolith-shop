package edu.cit.verano.shop;

/**
 * Request payload for placing an order.
 * Format: { "productId": "P100", "quantity": 2 }
 */
public record OrderRequest(
    String productId,
    int quantity
) {}
