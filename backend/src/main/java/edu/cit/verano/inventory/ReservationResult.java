package edu.cit.verano.inventory;

/**
 * Result of an inventory reservation attempt.
 */
public record ReservationResult(
    boolean successful,
    String message,
    InventoryItemDto inventory
) {
    public static ReservationResult success(InventoryItemDto inventory) {
        return new ReservationResult(true, "Reservation successful", inventory);
    }

    public static ReservationResult failure(String message, InventoryItemDto inventory) {
        return new ReservationResult(false, message, inventory);
    }
}
