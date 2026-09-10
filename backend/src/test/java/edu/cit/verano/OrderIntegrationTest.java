package edu.cit.verano;

import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import edu.cit.verano.shop.Order;
import edu.cit.verano.shop.OrderRepository;
import edu.cit.verano.shop.OrderRequest;
import edu.cit.verano.shop.OrderResponse;
import edu.cit.verano.shop.OrderService;
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
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Module Boundary: InventoryServiceImpl MUST be package-private")
    void testInventoryServiceImplIsPackagePrivate() throws ClassNotFoundException {
        Class<?> implClass = Class.forName("edu.cit.verano.inventory.InventoryServiceImpl");
        int modifiers = implClass.getModifiers();

        assertFalse(Modifier.isPublic(modifiers), "InventoryServiceImpl must NOT be public");
        assertFalse(Modifier.isPrivate(modifiers), "InventoryServiceImpl must NOT be private");
        assertFalse(Modifier.isProtected(modifiers), "InventoryServiceImpl must NOT be protected");
        // In Java, package-private has no public/protected/private modifiers
    }

    @Test
    @DisplayName("End-to-End Confirmed Order: P100 with quantity 2 is CONFIRMED and stock decrements")
    void testConfirmedOrderPath() {
        Optional<InventoryItemDto> initialItem = inventoryService.getItem("P100");
        assertTrue(initialItem.isPresent(), "P100 must exist");
        int initialStock = initialItem.get().stock();

        OrderRequest request = new OrderRequest("P100", 2);
        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertNotNull(response.inventory());
        assertEquals(initialStock - 2, response.inventory().stock());

        // Verify database persistence
        List<Order> orders = orderRepository.findAll();
        assertFalse(orders.isEmpty());
        Order latest = orders.get(orders.size() - 1);
        assertEquals("P100", latest.getProductId());
        assertEquals(2, latest.getQuantity());
        assertEquals("CONFIRMED", latest.getStatus());
    }

    @Test
    @DisplayName("End-to-End Rejected Order: P300 (stock 0) with quantity 1 is REJECTED")
    void testRejectedOrderOutOfStock() {
        OrderRequest request = new OrderRequest("P300", 1);
        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals("REJECTED", response.status());
        assertNotNull(response.reason());
        assertTrue(response.reason().contains("exceeds available stock"));
        assertNotNull(response.inventory());
        assertEquals(0, response.inventory().stock());
    }

    @Test
    @DisplayName("End-to-End Rejected Order: P200 (stock 10) with quantity 99 is REJECTED")
    void testRejectedOrderExcessQuantity() {
        OrderRequest request = new OrderRequest("P200", 99);
        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response);
        assertEquals("REJECTED", response.status());
        assertNotNull(response.reason());
        assertTrue(response.reason().contains("exceeds available stock"));
    }
}
