package edu.cit.verano.inventory;

import edu.cit.verano.events.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE implementation of InventoryService.
 * Enforces the modular monolith architectural boundary.
 * The Order module (edu.cit.verano.shop) cannot import or directly reference this class;
 * it can only access the service via the public InventoryService interface.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${shop.low-stock-threshold:5}")
    private int lowStockThreshold = 5;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItemDto> getItem(String productId) {
        return inventoryRepository.findById(productId)
                .map(InventoryItemDto::fromEntity);
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        if (productId == null || productId.trim().isEmpty()) {
            return ReservationResult.failure("Product ID cannot be null or empty", null);
        }

        if (quantity <= 0) {
            return ReservationResult.failure("Quantity must be greater than zero", null);
        }

        Optional<InventoryItem> optionalItem = inventoryRepository.findById(productId);
        if (optionalItem.isEmpty()) {
            return ReservationResult.failure("Product not found: " + productId, null);
        }

        InventoryItem item = optionalItem.get();
        if (item.getStock() < quantity) {
            return ReservationResult.failure(
                    String.format("Requested quantity (%d) exceeds available stock (%d)", quantity, item.getStock()),
                    InventoryItemDto.fromEntity(item)
            );
        }

        // Deduct stock and persist change
        item.setStock(item.getStock() - quantity);
        InventoryItem updated = inventoryRepository.save(item);

        // Low-Stock Auto-Reorder Rule: trigger event if remaining stock drops below threshold
        if (updated.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(new LowStockEvent(
                    updated.getProductId(),
                    updated.getName(),
                    updated.getStock(),
                    lowStockThreshold
            ));
        }

        return ReservationResult.success(InventoryItemDto.fromEntity(updated));
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return;
        }
        inventoryRepository.findById(productId).ifPresent(item -> {
            item.setStock(item.getStock() + quantity);
            inventoryRepository.save(item);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDto> getAllItems() {
        return inventoryRepository.findAll().stream()
                .map(InventoryItemDto::fromEntity)
                .toList();
    }
}
