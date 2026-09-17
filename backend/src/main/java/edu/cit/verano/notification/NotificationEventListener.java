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

    public NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
        String msg = String.format("REORDER NEEDED: Low stock alert for '%s' (%s) - remaining stock: %d (threshold: %d)",
                event.productName(), event.productId(), event.remainingStock(), event.threshold());
        notificationRepository.save(new Notification(msg));
        System.out.println("[NotificationModule] " + msg);
    }
}
