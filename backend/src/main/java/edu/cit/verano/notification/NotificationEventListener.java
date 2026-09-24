package edu.cit.verano.notification;

import edu.cit.verano.events.LowStockEvent;
import edu.cit.verano.events.OrderPlacedEvent;
import edu.cit.verano.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event Listener within the Notification module.
 * Strictly adheres to architectural boundaries:
 * Depends ONLY on event records (edu.cit.verano.events.*).
 * Never references OrderService or InventoryService.
 */
@Component
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final edu.cit.verano.supplier.SupplierGateway supplierGateway;

    public NotificationEventListener(NotificationRepository notificationRepository,
                                   edu.cit.verano.supplier.SupplierGateway supplierGateway) {
        this.notificationRepository = notificationRepository;
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        String msg = String.format("Order #%d confirmed with %d line item(s)",
                event.orderId(), event.items().size());
        notificationRepository.save(new Notification(msg));
        System.out.println("[NotificationModule] " + msg);
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        String msg = String.format("Order #%d rejected: %s",
                event.orderId(), event.reason());
        notificationRepository.save(new Notification(msg));
        System.out.println("[NotificationModule] " + msg);
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        // Auto-reorder rule: calls SupplierGateway instead of only logging
        int unitsNeeded = Math.max(10, (event.threshold() - event.remainingStock()) + 10);
        edu.cit.verano.supplier.SupplierOrderResult result = supplierGateway.orderReplenishment(event.productId(), unitsNeeded);

        String msg = String.format("REORDER NEEDED: Low stock alert for '%s' (%s) - remaining stock: %d (threshold: %d). Supplier order #%s placed (Status: %s, PO: %s)",
                event.productName(), event.productId(), event.remainingStock(), event.threshold(),
                result.orderId(), result.status(), result.poNumber());
        notificationRepository.save(new Notification(msg));
        System.out.println("[NotificationModule] " + msg);
    }

    @EventListener
    public void onSupplierOrderDelivered(edu.cit.verano.events.SupplierOrderDeliveredEvent event) {
        String msg = String.format("SUPPLIER DELIVERED: Restocked %d units for '%s' from Supplier PO #%s",
                event.unitsRestocked(), event.productId(), event.poNumber());
        notificationRepository.save(new Notification(msg));
        System.out.println("[NotificationModule] " + msg);
    }
}

