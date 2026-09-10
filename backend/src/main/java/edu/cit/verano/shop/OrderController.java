package edu.cit.verano.shop;

import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing order and inventory endpoints.
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
    private final OrderRepository orderRepository;

    public OrderController(OrderService orderService,
                           InventoryService inventoryService,
                           OrderRepository orderRepository) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    /**
     * Primary required endpoint:
     * POST /api/orders - request { productId, quantity } -> response { status, reason, inventory }
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Helper endpoint for the React frontend to fetch product list and current stock.
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryItemDto>> getInventory() {
        return ResponseEntity.ok(inventoryService.getAllItems());
    }

    /**
     * Helper endpoint to inspect recent order history.
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrders() {
        return ResponseEntity.ok(orderRepository.findAllByOrderByCreatedAtDesc());
    }
}
