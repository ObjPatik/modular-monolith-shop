package edu.cit.verano.shop;

import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import edu.cit.verano.inventory.ReservationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order Service orchestrating order placement.
 * Demonstrates in-process module integration:
 * It depends exclusively on the public InventoryService interface,
 * injected via its constructor, with zero awareness of InventoryServiceImpl.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        if (request == null || request.productId() == null || request.productId().trim().isEmpty()) {
            Order order = new Order(
                    request != null ? request.productId() : "UNKNOWN",
                    request != null ? request.quantity() : 0,
                    "REJECTED",
                    "Product ID cannot be null or empty"
            );
            orderRepository.save(order);
            return OrderResponse.rejected("Product ID cannot be null or empty", null);
        }

        if (request.quantity() <= 0) {
            Order order = new Order(
                    request.productId(),
                    request.quantity(),
                    "REJECTED",
                    "Quantity must be greater than zero"
            );
            orderRepository.save(order);
            return OrderResponse.rejected("Quantity must be greater than zero", null);
        }

        // Call the inventory module in-process via its public interface
        ReservationResult result = inventoryService.reserve(request.productId(), request.quantity());

        String status;
        String reason;

        if (result.successful()) {
            status = "CONFIRMED";
            reason = null;
        } else {
            status = "REJECTED";
            reason = result.message();
        }

        // Persist order result to orders table
        Order order = new Order(request.productId(), request.quantity(), status, reason);
        orderRepository.save(order);

        return new OrderResponse(status, reason, result.inventory());
    }
}
