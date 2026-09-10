package edu.cit.verano.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Package-private startup seeder to ensure the required baseline inventory
 * exists in the database (P100, P200, P300) if not already present.
 */
@Component
class InventorySeeder implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    InventorySeeder(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        seedIfMissing("P100", "Wireless Mouse", 25);
        seedIfMissing("P200", "Mechanical Keyboard", 10);
        seedIfMissing("P300", "USB-C Hub", 0);
    }

    private void seedIfMissing(String productId, String name, int stock) {
        if (!inventoryRepository.existsById(productId)) {
            inventoryRepository.save(new InventoryItem(productId, name, stock));
            System.out.printf("[InventorySeeder] Seeded product: %s (%s) with stock: %d%n", productId, name, stock);
        }
    }
}
