package edu.cit.verano.shop;

import edu.cit.verano.inventory.InventoryItemDto;
import java.util.List;

/**
 * REST response payload for order placement.
 * Response format: { "orderId": 1, "status": "CONFIRMED"|"REJECTED", "reason": "...", "items": [...], "inventory": [...] }
 */
public record OrderResponse(
    Long orderId,
    String status,
    String reason,
    List<OrderItemOutcomeDto> items,
    List<InventoryItemDto> inventory
) {
    public static OrderResponse confirmed(Long orderId, List<OrderItemOutcomeDto> items, List<InventoryItemDto> inventory) {
        return new OrderResponse(orderId, "CONFIRMED", null, items, inventory);
    }

    public static OrderResponse rejected(Long orderId, String reason, List<OrderItemOutcomeDto> items, List<InventoryItemDto> inventory) {
        return new OrderResponse(orderId, "REJECTED", reason, items, inventory);
    }
}
