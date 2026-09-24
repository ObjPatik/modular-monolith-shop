package edu.cit.verano.supplier;

/**
 * Domain enumeration representing the internal state of a supplier replenishment order.
 * Strictly decoupled from external LegacySupply status codes.
 */
public enum SupplierOrderStatus {
    /** Order registered locally, awaiting dispatch to supplier */
    PENDING,

    /** Accepted by external supplier (LegacySupply StatusCode 10) */
    SUBMITTED,

    /** Order items being picked at supplier warehouse (LegacySupply StatusCode 20) */
    PICKING,

    /** Dispatched / in transit from supplier (LegacySupply StatusCode 30) */
    SHIPPED,

    /** Received at inventory dock and restocked (LegacySupply StatusCode 40) */
    DELIVERED,

    /** Supplier cancelled order */
    CANCELLED,

    /** Terminal failure */
    FAILED,

    /** Unrecognized status code from external partner */
    UNKNOWN_STATUS
}
