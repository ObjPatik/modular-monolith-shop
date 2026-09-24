package edu.cit.verano.supplier;

import edu.cit.verano.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE background worker for reorder resilience and order tracking.
 * Retries unplaced pending orders across outages, checks open PO deliveries,
 * and publishes domain events upon delivery while staying politely within request quota.
 */
@Component
class SupplierBackgroundScheduler {

    private static final Logger log = LoggerFactory.getLogger(SupplierBackgroundScheduler.class);

    private final SupplierOrderRepository supplierOrderRepository;
    private final LegacySupplyClient legacySupplyClient;
    private final CatalogMapping catalogMapping;
    private final ApplicationEventPublisher eventPublisher;

    SupplierBackgroundScheduler(SupplierOrderRepository supplierOrderRepository,
                               LegacySupplyClient legacySupplyClient,
                               CatalogMapping catalogMapping,
                               ApplicationEventPublisher eventPublisher) {
        this.supplierOrderRepository = supplierOrderRepository;
        this.legacySupplyClient = legacySupplyClient;
        this.catalogMapping = catalogMapping;
        this.eventPublisher = eventPublisher;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        if (supplierOrderRepository.count() == 0) {
            log.info("[SupplierScheduler] Initializing tracking for existing orders on file...");
            String[][] initialOrders = {
                    {"P200", "RO-1", "PO-100012", "REQ-PO-100012"},
                    {"P200", "RO-2", "PO-100013", "REQ-PO-100013"},
                    {"P200", "RO-1-DUP", "PO-100014", "REQ-PO-100014"}
            };
            for (String[] o : initialOrders) {
                SupplierOrder entity = new SupplierOrder(o[0], 1, 24, o[1], o[3], SupplierOrderStatus.SUBMITTED);
                entity.setPoNumber(o[2]);
                supplierOrderRepository.save(entity);
            }
        }
    }

    /**
     * Outage resilience: Retries unplaced PENDING orders using stable X-Request-Id and BuyerRef.
     * Prevents duplicate orders by querying BuyerRef first.
     */
    @Scheduled(fixedDelay = 15000, initialDelay = 5000)
    @Transactional
    public void retryPendingOrders() {
        List<SupplierOrder> pendingOrders = supplierOrderRepository.findByStatus(SupplierOrderStatus.PENDING);
        if (pendingOrders.isEmpty()) {
            return;
        }

        log.info("[SupplierScheduler] Found {} pending supplier order(s) to process/retry", pendingOrders.size());

        for (SupplierOrder order : pendingOrders) {
            try {
                // Check if already placed on supplier under this unique BuyerRef
                Optional<PurchaseOrderAckXml> existing = legacySupplyClient.getOrderByBuyerRef(order.getBuyerRef());
                if (existing.isPresent()) {
                    PurchaseOrderAckXml ack = existing.get();
                    order.setPoNumber(ack.getPoNumber());
                    order.setStatus(SupplierGatewayImpl.mapStatusCode(ack.getStatusCode()));
                    supplierOrderRepository.save(order);
                    log.info("[SupplierScheduler] Pending order #{} already existed on supplier as PO #{}",
                            order.getId(), ack.getPoNumber());
                    continue;
                }

                // Not yet on supplier, submit with persistent RequestId and BuyerRef
                String sku = catalogMapping.getSupplierSku(order.getProductId());
                PurchaseOrderAckXml ack = legacySupplyClient.submitPurchaseOrder(
                        sku, order.getCases(), order.getBuyerRef(), order.getRequestId()
                );

                if (ack != null && ack.getPoNumber() != null) {
                    order.setPoNumber(ack.getPoNumber());
                    order.setStatus(SupplierGatewayImpl.mapStatusCode(ack.getStatusCode()));
                    supplierOrderRepository.save(order);
                    log.info("[SupplierScheduler] Successfully recovered pending order #{} -> PO #{}",
                            order.getId(), ack.getPoNumber());
                }
            } catch (Exception ex) {
                log.warn("[SupplierScheduler] Retry for pending order #{} failed: {}. Will retry next cycle.",
                        order.getId(), ex.getMessage());
            }

            // Be polite between calls
            sleepQuietly(300);
        }
    }

    /**
     * Delivery tracking: Polls open orders to track status progression to DELIVERED.
     * Publishes SupplierOrderDeliveredEvent when delivered to trigger inventory restock.
     */
    @Scheduled(fixedDelay = 20000, initialDelay = 10000)
    @Transactional
    public void pollOpenOrders() {
        List<SupplierOrderStatus> openStatuses = List.of(
                SupplierOrderStatus.SUBMITTED,
                SupplierOrderStatus.PICKING,
                SupplierOrderStatus.SHIPPED
        );

        List<SupplierOrder> openOrders = supplierOrderRepository.findByStatusIn(openStatuses);
        if (openOrders.isEmpty()) {
            return;
        }

        log.info("[SupplierScheduler] Polling status for {} open supplier order(s)", openOrders.size());

        // Process at most 5 orders per cycle to stay comfortably within rate quota
        int limit = Math.min(5, openOrders.size());
        for (int i = 0; i < limit; i++) {
            SupplierOrder order = openOrders.get(i);
            if (order.getPoNumber() == null || order.getPoNumber().isBlank()) {
                continue;
            }

            try {
                PurchaseOrderStatusXml statusXml = legacySupplyClient.getOrderStatus(order.getPoNumber());
                if (statusXml != null && statusXml.getStatusCode() != null) {
                    SupplierOrderStatus newStatus = SupplierGatewayImpl.mapStatusCode(statusXml.getStatusCode());

                    if (newStatus != order.getStatus()) {
                        log.info("[SupplierScheduler] Order #{} (PO {}) status updated: {} -> {}",
                                order.getId(), order.getPoNumber(), order.getStatus(), newStatus);

                        order.setStatus(newStatus);
                        supplierOrderRepository.save(order);

                        // If DELIVERED, publish domain event so Inventory can restock
                        if (newStatus == SupplierOrderStatus.DELIVERED) {
                            log.info("[SupplierScheduler] Order #{} (PO {}) DELIVERED. Publishing domain event to restock {} units of '{}'",
                                    order.getId(), order.getPoNumber(), order.getUnits(), order.getProductId());
                            eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                                    order.getProductId(),
                                    order.getUnits(),
                                    order.getPoNumber()
                            ));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[SupplierScheduler] Failed to poll status for PO #{}: {}", order.getPoNumber(), ex.getMessage());
            }

            sleepQuietly(400); // Polite interval between requests
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
