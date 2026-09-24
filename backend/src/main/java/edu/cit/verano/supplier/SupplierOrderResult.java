package edu.cit.verano.supplier;

/**
 * Domain outcome of a supplier replenishment request.
 * Contains only internal domain representations.
 */
public record SupplierOrderResult(
    Long orderId,
    String productId,
    int unitsRequested,
    int casesOrdered,
    SupplierOrderStatus status,
    String poNumber,
    String message
) {
    public static SupplierOrderResult of(Long orderId, String productId, int unitsRequested, int casesOrdered,
                                        SupplierOrderStatus status, String poNumber, String message) {
        return new SupplierOrderResult(orderId, productId, unitsRequested, casesOrdered, status, poNumber, message);
    }
}
