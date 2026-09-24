package edu.cit.verano.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Package-private startup seeder to ensure the required baseline inventory
 * exists in the database (P100, P200, P300) and replenishes depleted items.
 */
@Component
class InventorySeeder implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    InventorySeeder(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        replenishIfDepleted("P100", "Wireless Mouse", 25);
        replenishIfDepleted("P200", "Mechanical Keyboard", 10);
        seedIfMissing("P300", "USB-C Hub", 0);
    }

    private void replenishIfDepleted(String productId, String name, int defaultStock) {
        inventoryRepository.findById(productId).ifPresentOrElse(item -> {
            if (item.getStock() <= 0 && defaultStock > 0) {
                item.setStock(defaultStock);
                inventoryRepository.save(item);
                System.out.printf("[InventorySeeder] Replenished depleted product: %s to %d%n", productId, defaultStock);
            }
        }, () -> {
            inventoryRepository.save(new InventoryItem(productId, name, defaultStock));
            System.out.printf("[InventorySeeder] Seeded product: %s (%s) with stock: %d%n", productId, name, defaultStock);
        });
    }

    private void seedIfMissing(String productId, String name, int stock) {
        if (!inventoryRepository.existsById(productId)) {
            inventoryRepository.save(new InventoryItem(productId, name, stock));
            System.out.printf("[InventorySeeder] Seeded product: %s (%s) with stock: %d%n", productId, name, stock);
        }
    }
}
