package edu.cit.verano.supplier;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PACKAGE-PRIVATE Anti-Corruption translator mapping internal product IDs
 * to LegacySupply catalog items, pack sizes, and converting units to cases.
 */
@Component
class CatalogMapping {

    record SupplierItem(String supplierSku, String description, int packSize, double unitCost) {}

    // Static mapping discovered from GET /api/v1/catalog
    private final Map<String, SupplierItem> productToSupplierMap = new ConcurrentHashMap<>();

    CatalogMapping() {
        // P100: Wireless Mouse -> XFM-7477 (PackSize 10)
        productToSupplierMap.put("P100", new SupplierItem("XFM-7477", "WIRELESS MOUSE 2.4GHZ", 10, 450.00));
        // P200: Mechanical Keyboard -> XFM-7593 (PackSize 24)
        productToSupplierMap.put("P200", new SupplierItem("XFM-7593", "KEYBOARD MECH TKL", 24, 1899.00));
        // P300: USB-C Hub -> XFM-3161 (PackSize 6)
        productToSupplierMap.put("P300", new SupplierItem("XFM-3161", "USB HUB 4-PORT", 6, 399.00));
    }

    public boolean supportsProduct(String productId) {
        return productId != null && productToSupplierMap.containsKey(productId);
    }

    public SupplierItem getSupplierItem(String productId) {
        SupplierItem item = productToSupplierMap.get(productId);
        if (item == null) {
            throw new IllegalArgumentException("Unknown product for supplier replenishment: " + productId);
        }
        return item;
    }

    public String getSupplierSku(String productId) {
        return getSupplierItem(productId).supplierSku();
    }

    public int getPackSize(String productId) {
        return getSupplierItem(productId).packSize();
    }

    /**
     * Converts internal individual consumer units to LegacySupply order cases (Qty),
     * rounding up to ensure sufficient inventory is ordered.
     */
    public int toCases(String productId, int unitsNeeded) {
        if (unitsNeeded <= 0) {
            return 1;
        }
        int packSize = getPackSize(productId);
        int cases = (int) Math.ceil((double) unitsNeeded / packSize);
        // LegacySupply Qty must be between 1 and 99
        return Math.min(99, Math.max(1, cases));
    }

    /**
     * Converts cases delivered back into total individual units restocked.
     */
    public int toRestockUnits(String productId, int cases) {
        int packSize = getPackSize(productId);
        return cases * packSize;
    }
}
