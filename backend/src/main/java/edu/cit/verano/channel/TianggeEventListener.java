package edu.cit.verano.channel;

import edu.cit.verano.events.OrderCancelledEvent;
import edu.cit.verano.events.OrderPlacedEvent;
import edu.cit.verano.events.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PACKAGE-PRIVATE event listener responding to domain events for stock synchronization
 * and backorder fulfillment. Strictly event-driven, non-timer based.
 */
@Component
class TianggeEventListener {

    private static final Logger log = LoggerFactory.getLogger(TianggeEventListener.class);

    private final TianggeStockSync stockSync;
    private final TianggeBackorderManager backorderManager;

    TianggeEventListener(TianggeStockSync stockSync, TianggeBackorderManager backorderManager) {
        this.stockSync = stockSync;
        this.backorderManager = backorderManager;
    }

    @org.springframework.core.annotation.Order(10)
    @EventListener
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        log.info("[TianggeEventListener] Supplier delivery received for product '{}' ({} units). " +
                "Triggering backorder resolution and stock synchronization...",
                event.productId(), event.unitsRestocked());

        // 1. Resolve any pending backorders waiting for this product
        try {
            backorderManager.resolveBackordersForProduct(event.productId());
        } catch (Exception ex) {
            log.error("[TianggeEventListener] Error resolving backorders: {}", ex.getMessage(), ex);
        }

        // 2. Publish updated stock snapshot to Tiangge
        stockSync.publishStock();
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (Boolean.TRUE.equals(TianggeContext.IN_TIANGGE_PROCESSING.get())) {
            // Stock will be published immediately after Tiangge receives the ACCEPTED decision
            return;
        }
        log.info("[TianggeEventListener] Local/UI order placed (#{}). Publishing decremented stock to Tiangge...",
                event.orderId());
        stockSync.publishStock();
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (Boolean.TRUE.equals(TianggeContext.IN_TIANGGE_PROCESSING.get())) {
            // Stock will be published immediately after Tiangge receives the cancellation confirmation
            return;
        }
        log.info("[TianggeEventListener] Local/UI order cancelled (#{}). Publishing replenished stock to Tiangge...",
                event.orderId());
        stockSync.publishStock();
    }
}

