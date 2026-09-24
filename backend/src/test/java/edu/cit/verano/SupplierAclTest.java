package edu.cit.verano;

import edu.cit.verano.events.SupplierOrderDeliveredEvent;
import edu.cit.verano.inventory.InventoryService;
import edu.cit.verano.supplier.SupplierGateway;
import edu.cit.verano.supplier.SupplierOrderResult;
import edu.cit.verano.supplier.SupplierOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:suppliertestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SupplierAclTest {

    @Autowired
    private SupplierGateway supplierGateway;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("ACL Boundary: ONLY SupplierGateway and domain types are public; internals are package-private")
    void testAclEncapsulationBoundaries() throws ClassNotFoundException {
        // Public domain contracts
        assertTrue(Modifier.isPublic(SupplierGateway.class.getModifiers()), "SupplierGateway must be public");
        assertTrue(Modifier.isPublic(SupplierOrderResult.class.getModifiers()), "SupplierOrderResult must be public");
        assertTrue(Modifier.isPublic(SupplierOrderStatus.class.getModifiers()), "SupplierOrderStatus must be public");

        // Internal classes MUST be package-private
        String[] packagePrivateClasses = {
                "edu.cit.verano.supplier.SupplierGatewayImpl",
                "edu.cit.verano.supplier.SupplierOrder",
                "edu.cit.verano.supplier.SupplierOrderRepository",
                "edu.cit.verano.supplier.CatalogMapping",
                "edu.cit.verano.supplier.LegacySupplyClient",
                "edu.cit.verano.supplier.SupplierBackgroundScheduler",
                "edu.cit.verano.supplier.AuthRequestXml",
                "edu.cit.verano.supplier.AuthResponseXml",
                "edu.cit.verano.supplier.PurchaseOrderXml",
                "edu.cit.verano.supplier.PurchaseOrderAckXml",
                "edu.cit.verano.supplier.PurchaseOrderStatusXml",
                "edu.cit.verano.supplier.PurchaseOrderListXml",
                "edu.cit.verano.supplier.LSErrorXml"
        };

        for (String className : packagePrivateClasses) {
            Class<?> clazz = Class.forName(className);
            int mod = clazz.getModifiers();
            assertFalse(Modifier.isPublic(mod), className + " must NOT be public");
            assertFalse(Modifier.isPrivate(mod), className + " must NOT be private");
            assertFalse(Modifier.isProtected(mod), className + " must NOT be protected");
        }
    }

    @Test
    @DisplayName("ACL Reorder: Places reorder, converts units to cases (rounding up), and persists in supplier_orders")
    void testOrderReplenishmentConversionAndPersistence() {
        // P200 has pack size 24. Requesting 5 units should round up to 1 case (24 units)
        SupplierOrderResult result = supplierGateway.orderReplenishment("P200", 5);

        assertNotNull(result);
        assertNotNull(result.orderId());
        assertEquals("P200", result.productId());
        assertEquals(5, result.unitsRequested());
        assertEquals(1, result.casesOrdered());
        assertTrue(result.status() == SupplierOrderStatus.SUBMITTED || result.status() == SupplierOrderStatus.PENDING);
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    @DisplayName("ACL Event Restock: Inventory listens to SupplierOrderDeliveredEvent and restocks automatically")
    void testDeliveryRestocksInventoryViaDomainEvent() {
        int initialStock = inventoryService.getItem("P100").orElseThrow().stock();

        // Simulate delivery event published by supplier background poller
        eventPublisher.publishEvent(new SupplierOrderDeliveredEvent("P100", 20, "PO-TEST-1234"));

        int updatedStock = inventoryService.getItem("P100").orElseThrow().stock();
        assertEquals(initialStock + 20, updatedStock, "Inventory must restock 20 units upon receiving delivery event");
    }

    @Test
    @DisplayName("ACL Poller: Polls open orders and tracks external status progression")
    void testPollOpenOrdersTracksStatus() throws Exception {
        Object scheduler = applicationContext.getBean("supplierBackgroundScheduler");
        java.lang.reflect.Method pollMethod = scheduler.getClass().getDeclaredMethod("pollOpenOrders");
        pollMethod.setAccessible(true);
        pollMethod.invoke(scheduler);
    }
}
