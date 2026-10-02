package edu.cit.verano.channel;

import edu.cit.verano.channel.TianggeDtos.StockDto;
import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PACKAGE-PRIVATE component synchronizing current local inventory stock with Tiangge.
 * Invoked on domain events (orders placed, cancellations, supplier deliveries).
 * Strictly non-timer based.
 */
@Component
class TianggeStockSync {

    private static final Logger log = LoggerFactory.getLogger(TianggeStockSync.class);

    private final InventoryService inventoryService;
    private final TianggeClient tianggeClient;
    private final ReentrantLock syncLock = new ReentrantLock();

    private static final List<String> MANAGED_SKUS = List.of("P100", "P200", "P300");

    TianggeStockSync(InventoryService inventoryService, TianggeClient tianggeClient) {
        this.inventoryService = inventoryService;
        this.tianggeClient = tianggeClient;
    }

    /**
     * Publishes current inventory snapshot for all listed products to Tiangge.
     */
    void publishStock() {
        if (!syncLock.tryLock()) {
            // If another sync is in progress, acquire lock to ensure the latest numbers are published
            syncLock.lock();
        }
        try {
            Map<String, Integer> stockMap = new HashMap<>();
            for (String sku : MANAGED_SKUS) {
                stockMap.put(sku, 0);
            }

            for (InventoryItemDto item : inventoryService.getAllItems()) {
                if (stockMap.containsKey(item.productId())) {
                    stockMap.put(item.productId(), Math.max(0, item.stock()));
                }
            }

            List<StockDto> stockList = new ArrayList<>();
            for (String sku : MANAGED_SKUS) {
                stockList.add(new StockDto(sku, stockMap.getOrDefault(sku, 0)));
            }

            log.info("[TianggeStockSync] Syncing stock snapshot to Tiangge: {}", stockList);
            tianggeClient.publishStock(stockList);
        } catch (Exception ex) {
            log.error("[TianggeStockSync] Failed to publish stock to Tiangge: {}", ex.getMessage());
        } finally {
            syncLock.unlock();
        }
    }
}

