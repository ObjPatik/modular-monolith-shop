package edu.cit.verano.inventory;

import java.util.List;
import java.util.Optional;

/**
 * Public contract for the Inventory module.
 * The Order module is permitted to depend ONLY on this interface.
 */
public interface InventoryService {

    /**
     * Retrieves inventory details for the given product ID.
     */
    Optional<InventoryItemDto> getItem(String productId);

    /**
     * Attempts to reserve the specified quantity for a product.
     * Deducts stock and returns success if available;
     * rejects if requested quantity exceeds stock or product does not exist.
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Returns reserved stock back to inventory (used upon order cancellation).
     */
    void restock(String productId, int quantity);

    /**
     * Lists all inventory items (useful for frontend product dropdown).
     */
    List<InventoryItemDto> getAllItems();
}
