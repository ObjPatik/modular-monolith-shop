package edu.cit.verano.inventory;

/**
 * Public Data Transfer Object exposing product inventory details
 * across module boundaries without leaking direct JPA entities.
 */
public record InventoryItemDto(
    String productId,
    String name,
    int stock
) {
    public static InventoryItemDto fromEntity(InventoryItem item) {
        if (item == null) {
            return null;
        }
        return new InventoryItemDto(item.getProductId(), item.getName(), item.getStock());
    }
}
