package edu.cit.verano.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.verano.channel.TianggeDtos.FeedLine;
import edu.cit.verano.channel.TianggeDtos.ResolutionRequest;
import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import edu.cit.verano.shop.OrderItemRequest;
import edu.cit.verano.shop.OrderRequest;
import edu.cit.verano.shop.OrderResponse;
import edu.cit.verano.shop.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE manager responsible for resolving BACKORDERED Tiangge orders
 * when replenishment deliveries arrive from LegacySupply.
 */
@Component
class TianggeBackorderManager {

    private static final Logger log = LoggerFactory.getLogger(TianggeBackorderManager.class);

    private final TianggeOrderMappingRepository orderMappingRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final TianggeClient tianggeClient;
    private final TianggeStockSync stockSync;
    private final ObjectMapper objectMapper;

    TianggeBackorderManager(TianggeOrderMappingRepository orderMappingRepository,
                            OrderService orderService,
                            InventoryService inventoryService,
                            TianggeClient tianggeClient,
                            TianggeStockSync stockSync) {
        this.orderMappingRepository = orderMappingRepository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.tianggeClient = tianggeClient;
        this.stockSync = stockSync;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Periodic safety check to resolve any pending backorders as soon as stock becomes available.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000, initialDelay = 5000)
    @Transactional
    public void scheduledBackorderCheck() {
        resolvePendingBackorders();
    }

    /**
     * Attempts to resolve pending backorders for the delivered product or any available stock.
     * Orders are evaluated FIFO (oldest placedAt first).
     */
    @Transactional
    public void resolveBackordersForProduct(String productId) {
        resolvePendingBackorders();
    }

    /**
     * Evaluates all pending BACKORDERED orders in FIFO order and resolves them if stock permits.
     */
    @Transactional
    public void resolvePendingBackorders() {
        List<TianggeOrderMapping> backorders = orderMappingRepository.findByStatusOrderByPlacedAtAsc("BACKORDERED");
        if (backorders.isEmpty()) {
            return;
        }

        log.info("[TianggeBackorder] Checking {} pending backorder(s) for resolution...", backorders.size());

        for (TianggeOrderMapping order : backorders) {
            List<FeedLine> lines = parseLines(order.getLinesJson());
            if (lines.isEmpty()) {
                continue;
            }

            // Check if current inventory can satisfy all lines
            boolean canFill = true;
            List<OrderItemRequest> itemRequests = new ArrayList<>();
            for (FeedLine line : lines) {
                Optional<InventoryItemDto> itemOpt = inventoryService.getItem(line.sellerSku());
                if (itemOpt.isEmpty() || itemOpt.get().stock() < line.qty()) {
                    canFill = false;
                    break;
                }
                itemRequests.add(new OrderItemRequest(line.sellerSku(), line.qty()));
            }

            if (canFill) {
                log.info("[TianggeBackorder] Sufficient stock available to resolve backorder {}", order.getOrderId());
                TianggeContext.IN_TIANGGE_PROCESSING.set(true);
                Long localOrderId = null;
                try {
                    OrderResponse resp = orderService.placeOrder(new OrderRequest(itemRequests, null, null));
                    if ("CONFIRMED".equalsIgnoreCase(resp.status())) {
                        localOrderId = resp.orderId();
                        String localShopOrderId = "SO-" + localOrderId;

                        // 1. Send resolution to Tiangge FIRST
                        tianggeClient.sendResolution(order.getOrderId(), new ResolutionRequest("ACCEPTED"));

                        // 2. Update local mapping
                        order.setStatus("RESOLVED");
                        order.setShopOrderId(localShopOrderId);
                        order.setResolvedAt(Instant.now());
                        orderMappingRepository.save(order);

                        log.info("[TianggeBackorder] Backorder {} successfully resolved to ACCEPTED (Local: {})",
                                order.getOrderId(), localShopOrderId);

                        // 3. Publish updated stock AFTER resolution
                        stockSync.publishStock();
                    } else {
                        log.warn("[TianggeBackorder] Failed to confirm local order for backorder {}: {}",
                                order.getOrderId(), resp.reason());
                    }
                } catch (Exception ex) {
                    log.error("[TianggeBackorder] Error resolving backorder {}: {}", order.getOrderId(), ex.getMessage(), ex);
                    if (localOrderId != null) {
                        try {
                            orderService.cancelOrder(localOrderId);
                        } catch (Exception ignored) {}
                    }
                } finally {
                    TianggeContext.IN_TIANGGE_PROCESSING.set(false);
                }
            }
        }
    }

    private List<FeedLine> parseLines(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<FeedLine>>() {});
        } catch (Exception ex) {
            log.warn("[TianggeBackorder] Failed to parse lines JSON: {}", ex.getMessage());
            return List.of();
        }
    }
}

