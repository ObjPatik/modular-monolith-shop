package edu.cit.verano.supplier;

/**
 * Public Anti-Corruption Layer Gateway Interface.
 * Shields internal domains from LegacySupply XML schemas, SKUs, pack sizes, and error codes.
 */
public interface SupplierGateway {

    /**
     * Places a replenishment order using internal terms (product ID and consumer units needed).
     *
     * @param productId   the internal product identifier (e.g., "P100")
     * @param unitsNeeded the quantity of individual units needed
     * @return the domain outcome of the replenishment request
     */
    SupplierOrderResult orderReplenishment(String productId, int unitsNeeded);
}
