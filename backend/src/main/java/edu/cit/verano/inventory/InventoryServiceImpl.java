package edu.cit.verano.inventory;

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

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
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

        return ReservationResult.success(InventoryItemDto.fromEntity(updated));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDto> getAllItems() {
        return inventoryRepository.findAll().stream()
                .map(InventoryItemDto::fromEntity)
                .toList();
    }
}
