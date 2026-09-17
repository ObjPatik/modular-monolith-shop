package edu.cit.verano.shop;

import edu.cit.verano.events.OrderItemEventDto;
import edu.cit.verano.events.OrderPlacedEvent;
import edu.cit.verano.events.OrderRejectedEvent;
import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import edu.cit.verano.inventory.ReservationResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Order Service orchestrating multi-item orders, all-or-nothing rollback,
 * and order cancellation with inventory restocking.
 * Enforces boundary rules: depends only on InventoryService interface and publishes domain events.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                        OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Multi-item order placement with All-or-Nothing pre-validation.
     * If any single item exceeds stock, the entire order is rejected with zero reservations.
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        List<OrderItemRequest> lineItems = request != null ? request.getEffectiveItems() : List.of();

        if (lineItems.isEmpty()) {
            Order order = new Order("REJECTED", "Order must contain at least one line item");
            Order saved = orderRepository.save(order);
            eventPublisher.publishEvent(new OrderRejectedEvent(saved.getOrderId(), saved.getReason(), List.of()));
            return OrderResponse.rejected(saved.getOrderId(), saved.getReason(), List.of(), inventoryService.getAllItems());
        }

        // ==========================================
        // PHASE 1: ALL-OR-NOTHING VALIDATION
        // Validate every item before making any reservations
        // ==========================================
        String validationFailureReason = null;
        List<OrderItemOutcomeDto> outcomes = new ArrayList<>();

        for (OrderItemRequest item : lineItems) {
            if (item.productId() == null || item.productId().trim().isEmpty()) {
                validationFailureReason = "Product ID cannot be null or empty";
                break;
            }
            if (item.quantity() <= 0) {
                validationFailureReason = "Quantity must be greater than zero for product: " + item.productId();
                break;
            }

            Optional<InventoryItemDto> stockCheck = inventoryService.getItem(item.productId());
            if (stockCheck.isEmpty()) {
                validationFailureReason = "Product not found: " + item.productId();
                break;
            }

            InventoryItemDto currentItem = stockCheck.get();
            if (item.quantity() > currentItem.stock()) {
                validationFailureReason = String.format(
                        "Requested quantity (%d) for '%s' (%s) exceeds available stock (%d)",
                        item.quantity(), currentItem.name(), currentItem.productId(), currentItem.stock()
                );
                break;
            }
        }

        // If any item failed validation: REJECT entire order with NO partial reservations
        if (validationFailureReason != null) {
            Order order = new Order("REJECTED", validationFailureReason);
            for (OrderItemRequest item : lineItems) {
                order.addItem(new OrderItem(item.productId(), item.quantity()));
                outcomes.add(new OrderItemOutcomeDto(item.productId(), item.quantity(), "REJECTED_NO_STOCK"));
            }
            Order saved = orderRepository.save(order);

            // Publish Domain Event for Notification module
            List<OrderItemEventDto> eventItems = lineItems.stream()
                    .map(i -> new OrderItemEventDto(i.productId(), i.quantity()))
                    .toList();
            eventPublisher.publishEvent(new OrderRejectedEvent(saved.getOrderId(), validationFailureReason, eventItems));

            return OrderResponse.rejected(saved.getOrderId(), validationFailureReason, outcomes, inventoryService.getAllItems());
        }

        // ==========================================
        // PHASE 2: ATOMIC RESERVATION
        // All items passed validation -> reserve each item
        // ==========================================
        for (OrderItemRequest item : lineItems) {
            ReservationResult res = inventoryService.reserve(item.productId(), item.quantity());
            if (!res.successful()) {
                // Defensive fallback in case of concurrent stock changes: throw to roll back transaction
                throw new IllegalStateException("Unexpected stock reservation failure: " + res.message());
            }
            outcomes.add(new OrderItemOutcomeDto(item.productId(), item.quantity(), "RESERVED"));
        }

        Order order = new Order("CONFIRMED", null);
        for (OrderItemRequest item : lineItems) {
            order.addItem(new OrderItem(item.productId(), item.quantity()));
        }
        Order saved = orderRepository.save(order);

        // Publish Domain Event for Notification module
        List<OrderItemEventDto> eventItems = lineItems.stream()
                .map(i -> new OrderItemEventDto(i.productId(), i.quantity()))
                .toList();
        eventPublisher.publishEvent(new OrderPlacedEvent(saved.getOrderId(), eventItems));

        return OrderResponse.confirmed(saved.getOrderId(), outcomes, inventoryService.getAllItems());
    }

    /**
     * Cancels an order and restores reserved stock to inventory.
     * Throws 404 if not found, 409 if already cancelled or invalid.
     */
    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order with ID " + orderId + " not found"));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new OrderAlreadyCancelledException("Order #" + orderId + " has already been cancelled.");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new OrderAlreadyCancelledException("Cannot cancel order #" + orderId + " because its status is " + order.getStatus());
        }

        // Return reserved inventory to stock
        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
        }

        order.setStatus("CANCELLED");
        order.setReason("Order cancelled by customer. Inventory restocked.");
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}
