package edu.cit.verano.shop;

/**
 * Request DTO representing a line item in an order placement request.
 */
public record OrderItemRequest(
    String productId,
    int quantity
) {}
