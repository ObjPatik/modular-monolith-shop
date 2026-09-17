package edu.cit.verano.shop;

/**
 * Outcome representation for an individual line item in an order response.
 */
public record OrderItemOutcomeDto(
    String productId,
    int quantity,
    String outcome
) {}

