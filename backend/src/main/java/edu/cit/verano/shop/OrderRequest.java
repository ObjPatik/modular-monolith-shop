package edu.cit.verano.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for placing orders.
 * Supports multi-item list format: { "items": [{ "productId": "P100", "quantity": 2 }, ...] }
 * Also retains backwards-compatibility with single-item format: { "productId": "P100", "quantity": 2 }
 */
public record OrderRequest(
    List<OrderItemRequest> items,
    String productId,
    Integer quantity
) {
    public List<OrderItemRequest> getEffectiveItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (productId != null && !productId.isBlank() && quantity != null && quantity > 0) {
            List<OrderItemRequest> single = new ArrayList<>();
            single.add(new OrderItemRequest(productId, quantity));
            return single;
        }
        return List.of();
    }
}
