package edu.cit.verano.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * PACKAGE-PRIVATE implementation of the SupplierGateway interface.
 * Implements the Anti-Corruption Layer translation, persistence, and safe delivery.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private final SupplierOrderRepository supplierOrderRepository;
    private final LegacySupplyClient legacySupplyClient;
    private final CatalogMapping catalogMapping;

    SupplierGatewayImpl(SupplierOrderRepository supplierOrderRepository,
                        LegacySupplyClient legacySupplyClient,
                        CatalogMapping catalogMapping) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.legacySupplyClient = legacySupplyClient;
        this.catalogMapping = catalogMapping;
    }

    @Override
    @Transactional
    public SupplierOrderResult orderReplenishment(String productId, int unitsNeeded) {
        log.info("[SupplierGateway] Received replenishment request for product '{}', units needed: {}", productId, unitsNeeded);

        if (!catalogMapping.supportsProduct(productId)) {
            log.warn("[SupplierGateway] Product '{}' is not supported by LegacySupply catalog", productId);
            return SupplierOrderResult.of(null, productId, unitsNeeded, 0, SupplierOrderStatus.FAILED, null,
                    "Unsupported product SKU: " + productId);
        }

        int cases = catalogMapping.toCases(productId, unitsNeeded);
        int restockUnits = catalogMapping.toRestockUnits(productId, cases);
        String sku = catalogMapping.getSupplierSku(productId);

        // 1. Create and persist initial record in PENDING state to guarantee zero lost reorders
        String tempRef = "RO-TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String tempReqId = "REQ-TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        SupplierOrder order = new SupplierOrder(productId, cases, restockUnits, tempRef, tempReqId, SupplierOrderStatus.PENDING);
        order = supplierOrderRepository.saveAndFlush(order);

        // BuyerRef must be unique per reorder across all environments and restarts (max 40 chars)
        String buyerRef = "RO-" + System.currentTimeMillis() + "-" + order.getId();
        // RequestId is unique and permanent across all retries/restarts (max 80 chars)
        String requestId = "REQ-" + order.getId() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        order.setBuyerRef(buyerRef);
        order.setRequestId(requestId);
        order = supplierOrderRepository.saveAndFlush(order);

        log.info("[SupplierGateway] Created supplier order #{} with BuyerRef '{}', RequestId '{}', Cases: {}",
                order.getId(), buyerRef, requestId, cases);

        // 2. Pre-check: Verify if this buyerRef already exists on LegacySupply to prevent any duplicates
        Optional<PurchaseOrderAckXml> existing = legacySupplyClient.getOrderByBuyerRef(buyerRef);
        if (existing.isPresent()) {
            PurchaseOrderAckXml existingAck = existing.get();
            SupplierOrderStatus existingStatus = mapStatusCode(existingAck.getStatusCode());
            order.setPoNumber(existingAck.getPoNumber());
            order.setStatus(existingStatus);
            supplierOrderRepository.save(order);
            log.info("[SupplierGateway] Order already exists on supplier as PO #{}, status: {}",
                    existingAck.getPoNumber(), existingStatus);
            return SupplierOrderResult.of(order.getId(), productId, unitsNeeded, cases, existingStatus,
                    existingAck.getPoNumber(), "Order already exists on supplier: " + existingAck.getPoNumber());
        }

        // 2. Attempt immediate dispatch to LegacySupply with retries and timeout
        try {
            PurchaseOrderAckXml ack = legacySupplyClient.submitPurchaseOrder(sku, cases, buyerRef, requestId);
            if (ack != null && ack.getPoNumber() != null) {
                SupplierOrderStatus mappedStatus = mapStatusCode(ack.getStatusCode());
                order.setPoNumber(ack.getPoNumber());
                order.setStatus(mappedStatus);
                supplierOrderRepository.save(order);

                log.info("[SupplierGateway] Order #{} successfully acknowledged by supplier. PO: {}, Status: {}",
                        order.getId(), ack.getPoNumber(), mappedStatus);

                return SupplierOrderResult.of(order.getId(), productId, unitsNeeded, cases, mappedStatus,
                        ack.getPoNumber(), "Successfully placed with LegacySupply: " + ack.getPoNumber());
            }
        } catch (Exception ex) {
            log.warn("[SupplierGateway] Direct supplier dispatch failed for order #{} (BuyerRef: {}). " +
                    "Order will remain PENDING and be retried by scheduled resilience worker. Cause: {}",
                    order.getId(), buyerRef, ex.getMessage());
        }

        // Return PENDING result if supplier is unreachable / in outage
        return SupplierOrderResult.of(order.getId(), productId, unitsNeeded, cases, SupplierOrderStatus.PENDING,
                null, "Supplier temporarily unavailable. Order kept as PENDING for background retry.");
    }

    static SupplierOrderStatus mapStatusCode(String code) {
        if (code == null) return SupplierOrderStatus.SUBMITTED;
        return switch (code.trim()) {
            case "10" -> SupplierOrderStatus.SUBMITTED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            case "90", "cancelled", "CANCELLED" -> SupplierOrderStatus.CANCELLED;
            default -> SupplierOrderStatus.UNKNOWN_STATUS;
        };
    }
}
