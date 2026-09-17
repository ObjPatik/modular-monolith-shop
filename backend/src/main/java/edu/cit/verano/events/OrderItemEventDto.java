package edu.cit.verano.events;

/**
 * Lightweight DTO representing a line item in domain events.
 */
public record OrderItemEventDto(
    String productId,
    int quantity
) {}
