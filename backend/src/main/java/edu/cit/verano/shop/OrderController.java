package edu.cit.verano.shop;

import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing order placement, cancellation, and inventory endpoints.
 * Configured with CORS enabled for the React dev server (http://localhost:5173).
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(
    origins = {
        "http://localhost:5173",
        "http://localhost:3000",
        "http://127.0.0.1:5173"
    },
    allowedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
public class OrderController {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    public OrderController(OrderService orderService,
                           InventoryService inventoryService) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    /**
     * Multi-Item Order placement endpoint:
     * POST /api/orders - request { items: [{ productId, quantity }, ...] }
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Order cancellation endpoint:
     * POST /api/orders/{orderId}/cancel -> sets status to CANCELLED and restocks inventory.
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long orderId) {
        Order cancelled = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(cancelled);
    }

    /**
     * Live inventory query endpoint:
     * GET /api/inventory -> returns all products with current stock.
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryItemDto>> getInventory() {
        return ResponseEntity.ok(inventoryService.getAllItems());
    }

    /**
     * Order history query endpoint:
     * GET /api/orders -> returns order history with status and line items.
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
