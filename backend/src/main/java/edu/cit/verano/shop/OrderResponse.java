package edu.cit.verano.shop;

import edu.cit.verano.inventory.InventoryItemDto;

/**
 * REST response payload for order placement.
 * Response: { "status": "CONFIRMED"|"REJECTED", "reason": "...", "inventory": { ... } }
 */
public record OrderResponse(
    String status,
    String reason,
    InventoryItemDto inventory
) {
    public static OrderResponse confirmed(InventoryItemDto inventory) {
        return new OrderResponse("CONFIRMED", null, inventory);
    }

    public static OrderResponse rejected(String reason, InventoryItemDto inventory) {
        return new OrderResponse("REJECTED", reason, inventory);
    }
}
