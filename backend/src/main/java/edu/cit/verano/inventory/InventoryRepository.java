package edu.cit.verano.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Package-private repository: only accessible within edu.cit.verano.inventory.
 * External modules (like Order module) cannot access this repository directly.
 */
@Repository
interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}
