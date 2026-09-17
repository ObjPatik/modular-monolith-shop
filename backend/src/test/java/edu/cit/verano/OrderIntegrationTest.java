package edu.cit.verano;

import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import edu.cit.verano.notification.Notification;
import edu.cit.verano.notification.NotificationRepository;
import edu.cit.verano.shop.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "shop.low-stock-threshold=5"
})
class OrderIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("Module Boundary: InventoryServiceImpl MUST be package-private")
    void testInventoryServiceImplIsPackagePrivate() throws ClassNotFoundException {
        Class<?> implClass = Class.forName("edu.cit.verano.inventory.InventoryServiceImpl");
        int modifiers = implClass.getModifiers();

        assertFalse(Modifier.isPublic(modifiers), "InventoryServiceImpl must NOT be public");
        assertFalse(Modifier.isPrivate(modifiers), "InventoryServiceImpl must NOT be private");
        assertFalse(Modifier.isProtected(modifiers), "InventoryServiceImpl must NOT be protected");
    }

    @Test
    @DisplayName("Multi-Item Order: All items valid -> CONFIRMED and stock decrements atomically")
    void testMultiItemConfirmedOrder() {
        int initialP100 = inventoryService.getItem("P100").orElseThrow().stock();
        int initialP200 = inventoryService.getItem("P200").orElseThrow().stock();

        OrderRequest request = new OrderRequest(
                List.of(
                        new OrderItemRequest("P100", 2),
                        new OrderItemRequest("P200", 1)
                ), null, null
        );

        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertEquals(2, response.items().size());

        // Verify stock decremented
        assertEquals(initialP100 - 2, inventoryService.getItem("P100").orElseThrow().stock());
        assertEquals(initialP200 - 1, inventoryService.getItem("P200").orElseThrow().stock());

        // Verify Notification logged via event
        List<Notification> notifications = notificationRepository.findAll();
        boolean hasConfirmedNotification = notifications.stream()
                .anyMatch(n -> n.getMessage().contains("confirmed with 2 line item(s)"));
        assertTrue(hasConfirmedNotification, "Notification feed must contain confirmed order event log");
    }

    @Test
    @DisplayName("All-or-Nothing Rollback: If one item exceeds stock, ENTIRE order rejected with ZERO stock changes")
    void testAllOrNothingRollbackWhenOneItemFails() {
        int initialP100 = inventoryService.getItem("P100").orElseThrow().stock();
        int initialP300 = inventoryService.getItem("P300").orElseThrow().stock(); // 0 in stock

        // P100 has sufficient stock (initialP100 >= 2), but P300 has 0 stock
        OrderRequest request = new OrderRequest(
                List.of(
                        new OrderItemRequest("P100", 2),
                        new OrderItemRequest("P300", 1)
                ), null, null
        );

        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals("REJECTED", response.status());
        assertNotNull(response.reason());
        assertTrue(response.reason().contains("exceeds available stock") || response.reason().contains("P300"));

        // ALL-OR-NOTHING CRITICAL ASSERTION: P100 must NOT have been reserved!
        assertEquals(initialP100, inventoryService.getItem("P100").orElseThrow().stock(),
                "Stock for P100 MUST remain unchanged when order is rejected!");
        assertEquals(initialP300, inventoryService.getItem("P300").orElseThrow().stock());

        // Verify rejection logged in notifications
        boolean hasRejectedNotification = notificationRepository.findAll().stream()
                .anyMatch(n -> n.getMessage().contains("rejected:"));
        assertTrue(hasRejectedNotification, "Notification feed must record rejection event");
    }

    @Test
    @DisplayName("Order Cancellation & Restock: POST /api/orders/{id}/cancel returns items to inventory")
    void testOrderCancellationAndRestock() {
        int initialP100 = inventoryService.getItem("P100").orElseThrow().stock();

        OrderRequest request = new OrderRequest(
                List.of(new OrderItemRequest("P100", 3)), null, null
        );
        OrderResponse response = orderService.placeOrder(request);
        assertEquals("CONFIRMED", response.status());
        assertEquals(initialP100 - 3, inventoryService.getItem("P100").orElseThrow().stock());

        Long orderId = response.orderId();
        Order cancelled = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", cancelled.getStatus());
        // Verify inventory is restocked
        assertEquals(initialP100, inventoryService.getItem("P100").orElseThrow().stock(),
                "Inventory must be fully restored upon cancellation");

        // Verify duplicate cancellation throws 409 Conflict exception
        assertThrows(OrderAlreadyCancelledException.class, () -> orderService.cancelOrder(orderId));
    }

    @Test
    @DisplayName("Cancellation 404: Cancelling non-existent order throws OrderNotFoundException")
    void testCancelNonExistentOrderThrows404() {
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(999999L));
    }

    @Test
    @DisplayName("Low-Stock Event: Stock dropping below threshold publishes LowStockEvent and logs reorder alert")
    void testLowStockAlertTriggered() {
        // P200 has 10 (or 9) units initially. Threshold is 5.
        // If we order enough so remaining stock is < 5:
        int currentP200 = inventoryService.getItem("P200").orElseThrow().stock();
        int orderQty = currentP200 - 3; // Leaves remaining stock at 3 (< 5)

        OrderRequest request = new OrderRequest(
                List.of(new OrderItemRequest("P200", orderQty)), null, null
        );
        OrderResponse response = orderService.placeOrder(request);
        assertEquals("CONFIRMED", response.status());

        // Verify remaining stock is 3
        assertEquals(3, inventoryService.getItem("P200").orElseThrow().stock());

        // Verify Low-Stock alert is logged in notification table
        boolean hasLowStockAlert = notificationRepository.findAll().stream()
                .anyMatch(n -> n.getMessage().contains("REORDER NEEDED") && n.getMessage().contains("P200"));
        assertTrue(hasLowStockAlert, "Notification module must log REORDER NEEDED low stock alert");
    }
}
