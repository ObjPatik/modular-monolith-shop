package edu.cit.verano.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.verano.channel.TianggeDtos.*;
import edu.cit.verano.inventory.InventoryItemDto;
import edu.cit.verano.inventory.InventoryService;
import edu.cit.verano.shop.OrderItemRequest;
import edu.cit.verano.shop.OrderRequest;
import edu.cit.verano.shop.OrderResponse;
import edu.cit.verano.shop.OrderService;
import edu.cit.verano.supplier.SupplierGateway;
import edu.cit.verano.supplier.SupplierOrderResult;
import edu.cit.verano.supplier.SupplierOrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE processor for incoming Tiangge feed orders and cancellations.
 * Enforces all-or-nothing reservations, supplier backorder checks, and immediate stock sync.
 */
@Component
class TianggeOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(TianggeOrderProcessor.class);

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeClient tianggeClient;
    private final TianggeStockSync stockSync;
    private final TianggeOrderMappingRepository orderMappingRepository;
    private final ObjectMapper objectMapper;

    TianggeOrderProcessor(OrderService orderService,
                          InventoryService inventoryService,
                          SupplierGateway supplierGateway,
                          TianggeClient tianggeClient,
                          TianggeStockSync stockSync,
                          TianggeOrderMappingRepository orderMappingRepository) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.tianggeClient = tianggeClient;
        this.stockSync = stockSync;
        this.orderMappingRepository = orderMappingRepository;
        this.objectMapper = new ObjectMapper();
    }

    boolean isOrderAlreadyDecided(String orderId) {
        return orderId != null && orderMappingRepository.findByOrderId(orderId).isPresent();
    }

    /**
     * Decides an ORDER_PLACED feed event within 60s of arrival.
     */
    void processOrderPlaced(FeedEvent event) {
        String orderId = event.orderId();
        log.info("[TianggeOrderProcessor] Processing ORDER_PLACED event: {} (seq: {})", orderId, event.seq());

        // Idempotency: check if this Tiangge order was already decided
        Optional<TianggeOrderMapping> existing = orderMappingRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            TianggeOrderMapping mapping = existing.get();
            log.info("[TianggeOrderProcessor] Order {} was already decided as '{}' (ShopOrderId: {}). Safely ignoring duplicate.",
                    orderId, mapping.getStatus(), mapping.getShopOrderId());
            return;
        }

        List<FeedLine> lines = event.lines() != null ? event.lines() : List.of();
        if (lines.isEmpty()) {
            rejectOrder(event, "No line items in order");
            return;
        }

        // Phase 1: Check available inventory stock for ALL lines
        boolean allStockAvailable = true;
        List<OrderItemRequest> orderItems = new ArrayList<>();

        for (FeedLine line : lines) {
            Optional<InventoryItemDto> itemOpt = inventoryService.getItem(line.sellerSku());
            if (itemOpt.isEmpty() || itemOpt.get().stock() < line.qty()) {
                allStockAvailable = false;
            }
            orderItems.add(new OrderItemRequest(line.sellerSku(), line.qty()));
        }

        // Case A: Sufficient stock -> ACCEPT and reserve through OrderService
        if (allStockAvailable) {
            log.info("[TianggeOrderProcessor] All items available for order {}. Placing real order in Order module...", orderId);
            TianggeContext.IN_TIANGGE_PROCESSING.set(true);
            try {
                OrderResponse response = orderService.placeOrder(new OrderRequest(orderItems, null, null));
                if ("CONFIRMED".equalsIgnoreCase(response.status())) {
                    String shopOrderId = "SO-" + response.orderId();

                    // 1. Send decision to Tiangge FIRST
                    tianggeClient.sendDecision(orderId, new DecisionRequest("ACCEPTED", shopOrderId, null));

                    // 2. Persist order mapping
                    saveOrderMapping(event, shopOrderId, "ACCEPTED");

                    log.info("[TianggeOrderProcessor] Order {} ACCEPTED -> local order #{}", orderId, response.orderId());

                    // 3. Immediately publish updated stock AFTER Tiangge receives the ACCEPTED decision
                    stockSync.publishStock();
                    return;
                } else {
                    log.warn("[TianggeOrderProcessor] OrderService rejected order {}: {}", orderId, response.reason());
                }
            } catch (Exception ex) {
                log.error("[TianggeOrderProcessor] Error placing order in OrderService for {}: {}", orderId, ex.getMessage(), ex);
            } finally {
                TianggeContext.IN_TIANGGE_PROCESSING.set(false);
            }
        }

        // Case B & C: Stock insufficient -> Evaluate Backorder eligibility or Reject
        handleShortStockOrder(event, lines);
    }

    private void handleShortStockOrder(FeedEvent event, List<FeedLine> lines) {
        String orderId = event.orderId();
        boolean canBackorderAll = true;

        for (FeedLine line : lines) {
            Optional<InventoryItemDto> itemOpt = inventoryService.getItem(line.sellerSku());
            int currentStock = itemOpt.map(InventoryItemDto::stock).orElse(0);

            if (currentStock < line.qty()) {
                // Check if an open purchase order already exists
                boolean hasOpenPo = supplierGateway.hasOpenOrderForProduct(line.sellerSku());
                if (!hasOpenPo) {
                    // Attempt immediate replenishment to open a PO
                    int needed = line.qty() - currentStock;
                    log.info("[TianggeOrderProcessor] Item '{}' short by {}. Requesting supplier replenishment...",
                            line.sellerSku(), needed);
                    try {
                        SupplierOrderResult reorderResult = supplierGateway.orderReplenishment(line.sellerSku(), needed);
                        if (reorderResult.status() == SupplierOrderStatus.SUBMITTED ||
                            reorderResult.status() == SupplierOrderStatus.PICKING ||
                            reorderResult.status() == SupplierOrderStatus.SHIPPED ||
                            reorderResult.poNumber() != null) {
                            hasOpenPo = true;
                            log.info("[TianggeOrderProcessor] Supplier replenishment successful for '{}'. PO: {}",
                                    line.sellerSku(), reorderResult.poNumber());
                        } else {
                            log.warn("[TianggeOrderProcessor] Replenishment failed for '{}': {}",
                                    line.sellerSku(), reorderResult.message());
                        }
                    } catch (Exception ex) {
                        log.warn("[TianggeOrderProcessor] Replenishment error for '{}': {}", line.sellerSku(), ex.getMessage());
                    }
                }

                if (!hasOpenPo) {
                    canBackorderAll = false;
                    break;
                }
            }
        }

        if (canBackorderAll) {
            // Can answer BACKORDERED with confidence that supplier PO is in progress
            String shopOrderId = "BO-" + orderId;
            log.info("[TianggeOrderProcessor] Deciding order {} as BACKORDERED (open PO in progress)", orderId);

            tianggeClient.sendDecision(orderId, new DecisionRequest("BACKORDERED", shopOrderId, "Supplier replenishment order in progress"));
            saveOrderMapping(event, shopOrderId, "BACKORDERED");
        } else {
            // Cannot backorder without open PO -> must REJECT
            rejectOrder(event, "Requested items out of stock and replenishment unavailable");
        }
    }

    private void rejectOrder(FeedEvent event, String reason) {
        String orderId = event.orderId();
        String shopOrderId = "REJ-" + orderId;
        log.info("[TianggeOrderProcessor] Deciding order {} as REJECTED. Reason: {}", orderId, reason);

        tianggeClient.sendDecision(orderId, new DecisionRequest("REJECTED", shopOrderId, reason));
        saveOrderMapping(event, shopOrderId, "REJECTED");
    }

    /**
     * Confirms an ORDER_CANCELLED event and restores reserved inventory.
     */
    void processOrderCancelled(FeedEvent event) {
        String orderId = event.orderId();
        log.info("[TianggeOrderProcessor] Processing ORDER_CANCELLED event for {}", orderId);

        Optional<TianggeOrderMapping> existing = orderMappingRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            TianggeOrderMapping mapping = existing.get();
            if ("ACCEPTED".equalsIgnoreCase(mapping.getStatus()) && mapping.getShopOrderId() != null && mapping.getShopOrderId().startsWith("SO-")) {
                try {
                    Long localOrderId = Long.parseLong(mapping.getShopOrderId().substring(3));
                    TianggeContext.IN_TIANGGE_PROCESSING.set(true);
                    orderService.cancelOrder(localOrderId);
                    log.info("[TianggeOrderProcessor] Cancelled local order #{} for Tiangge {}", localOrderId, orderId);
                } catch (Exception ex) {
                    log.warn("[TianggeOrderProcessor] Local order cancellation notice: {}", ex.getMessage());
                } finally {
                    TianggeContext.IN_TIANGGE_PROCESSING.set(false);
                }
            } else if ("BACKORDERED".equalsIgnoreCase(mapping.getStatus())) {
                log.info("[TianggeOrderProcessor] Cancelled pending backorder for Tiangge {}", orderId);
            }
            mapping.setStatus("CANCELLED_BY_CUSTOMER");
            mapping.setCancelledAt(Instant.now());
            orderMappingRepository.save(mapping);
        }

        // 1. Confirm cancellation to Tiangge FIRST
        try {
            tianggeClient.sendCancellation(orderId, new CancellationRequest(true));
            log.info("[TianggeOrderProcessor] Cancellation confirmed to Tiangge for order {}", orderId);
        } catch (Exception ex) {
            log.warn("[TianggeOrderProcessor] Tiangge cancellation confirmation warning: {}", ex.getMessage());
        }

        // 2. Publish updated stock numbers AFTER cancellation is confirmed
        stockSync.publishStock();
    }

    private void saveOrderMapping(FeedEvent event, String shopOrderId, String status) {
        String linesJson = "";
        try {
            linesJson = objectMapper.writeValueAsString(event.lines());
        } catch (Exception ignored) {}

        String buyerName = event.buyer() != null ? event.buyer().name() : null;
        String buyerCity = event.buyer() != null ? event.buyer().city() : null;

        TianggeOrderMapping mapping = new TianggeOrderMapping(
                event.orderId(),
                shopOrderId,
                status,
                linesJson,
                buyerName,
                buyerCity,
                event.placedAt(),
                event.decisionDeadline()
        );
        orderMappingRepository.save(mapping);
    }
}

