# Lab 4 Reflection & Analysis: Marketplace (Tiangge)

**Student Name:** Verano  
**Student ID (ClientId):** `22-6077-335`  
**Repository:** Lab 4 Modular Monolith Shop - Tiangge Marketplace Integration  

---

## Part 1: Marketplace Reflection Prompts

### Question 1
> **Event evt_ca760559a32db586 (order TG-BJGNYZ) reached your application twice, as seq 3 and seq 4, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.**

#### Response
In [`TianggeFeedPoller.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeFeedPoller.java), before invoking `orderProcessor.processOrderPlaced(event)`, the application verifies `if (processedEventRepository.existsById(event.eventId()))` as well as checking `if (orderProcessor.isOrderAlreadyDecided(event.orderId()))`. When sequence 3 first arrived with `eventId = 'evt_ca760559a32db586'`, [`TianggeOrderProcessor.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeOrderProcessor.java) processed the order, created the local shop order via [`OrderService.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/shop/OrderService.java), and inserted a new `TianggeProcessedEvent` entity into the durable H2 database table `tiangge_processed_events` storing `event_id`, `event_type`, `order_id`, and `processed_at`. When sequence 4 arrived containing the exact same `eventId`, `processedEventRepository.existsById("evt_ca760559a32db586")` evaluated to `true`, logging `[TianggeFeedPoller] Event evt_ca760559a32db586 already processed. Skipping duplicate delivery.` and updating the feed cursor in `tiangge_feed_state` without executing duplicate business logic or sending redundant decisions. Additionally, `TianggeOrderMappingRepository` enforces a unique primary key constraint on `order_id` in `tiangge_orders`, and [`TianggeOrderProcessor.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeOrderProcessor.java) detects existing order IDs to prevent issuing secondary decision requests. If the application had restarted between sequence 3 and sequence 4, the second delivery would still be completely harmless because `tiangge_processed_events` and `tiangge_orders` are stored on disk in the persistent database file (`./data/shopdb.mv.db`) rather than volatile RAM. Upon restarting, the new application instance queries the existing database records, immediately identifies `evt_ca760559a32db586` as already processed, and safely ignores it.

---

### Question 2
> **Order TG-PRD5HH was backordered at 22:02:11 and accepted at 22:14:06, after PO-101991 was delivered at 22:08:11. Trace how the delivery reached your Inventory and what then resumed the backordered order.**

#### Response
Delivery tracking is driven by [`SupplierBackgroundScheduler.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/supplier/SupplierBackgroundScheduler.java), which periodically polls LegacySupply via `legacySupplyClient.getOrderStatus("PO-101991")`. When the supplier returned StatusCode 40 (Delivered), `SupplierBackgroundScheduler` updated the `SupplierOrder` entity status to `DELIVERED` and published an internal domain event `new SupplierOrderDeliveredEvent("P300", 6, "PO-101991")`. This event was first received by [`InventoryServiceImpl.onSupplierOrderDelivered()`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/inventory/InventoryServiceImpl.java) (ordered with `@Order(1)`), which called `restock("P300", 6)`, committing the 6 delivered units directly into the `inventory` database table. Next, [`TianggeEventListener.onSupplierOrderDelivered()`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeEventListener.java) (ordered with `@Order(10)`) triggered [`TianggeBackorderManager.resolvePendingBackorders()`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeBackorderManager.java) (which also executes on a periodic safety schedule every 5 seconds). `TianggeBackorderManager` queried `orderMappingRepository.findByStatusOrderByPlacedAtAsc("BACKORDERED")`, selected `TG-PRD5HH` in FIFO order, verified that inventory had sufficient units, and placed an internal shop order via [`OrderService.placeOrder()`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/shop/OrderService.java) to reserve stock under local order `SO-33`. It then transmitted `POST /tiangge/v1/orders/TG-PRD5HH/resolution` with `{"status": "ACCEPTED"}` via `TianggeClient.sendResolution()`, marked the order as `RESOLVED` in `tiangge_orders`, and triggered `stockSync.publishStock()` to publish the remaining available stock snapshot to Tiangge via `PUT /stock`.

---

### Question 3
> **During your restart test your application was down for about 109 seconds while 4 orders arrived. How did the restarted application find those orders, and how did it avoid handling earlier ones again?**

#### Response
Feed progress is persistently maintained in the durable `tiangge_feed_state` database table through the `TianggeFeedState` entity. Prior to stopping for the restart test, the active instance had processed events up to sequence 28 and recorded `last_cursor = 28` in the persistent H2 database file. When the application started again after 109 seconds of downtime, `TianggeFeedPoller.initCursorIfNecessary()` loaded the stored cursor `28` from `TianggeFeedStateRepository` and issued `GET /tiangge/v1/feed?after=28&limit=20`. Tiangge responded with precisely the 4 events that arrived during the offline gap (sequences 29 through 32: `TG-XNU4BC`, `TG-MHCJSG`, `TG-C4BFNU`, and `TG-8FT3QL`), allowing the restarted instance (`438d880d-c609-468f-ad95-e45e1da80bce`) to immediately evaluate inventory and submit decisions within deadlines. The application avoided re-handling earlier orders because passing `after=28` instructed Tiangge's feed endpoint to strictly return events with sequence numbers greater than 28. Furthermore, all historical event IDs (sequences 1 through 28) were preserved in the persistent `tiangge_processed_events` table, guaranteeing that any redeliveries of prior orders would be immediately recognized and skipped.

---

## Part 2: Verification Evidence & Requirement Compliance Analysis

Below is the audit trail and technical breakdown of how the application satisfied all requirements of Lab 4, as captured and verified on the live self-check portal (`https://legacysupply.onrender.com/verify`):

### 1. Live Self-Check Results (100% Passed Evidence)

During the live evaluation, the application achieved **13 out of 13 checks passed (100% MET)** under active evaluation:

| Verification Metric | Result / Proof | How the Application Satisfied the Requirement |
| :--- | :---: | :--- |
| **App Online (Stage 1)** | <kbd>**MET**</kbd> (100% uptime) | [`TianggeHeartbeatService.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeHeartbeatService.java) generates a new UUID on startup and sends heartbeats every 30 seconds to `/instances/heartbeat` with `uptimeSeconds`. |
| **All calls from running app** | <kbd>**MET**</kbd> (0 rogue calls) | `AppInstance.java` attaches `X-Client-Instance` to every outbound HTTP call (`RestTemplate` interceptor / header creation). |
| **Listings Published** | <kbd>**MET**</kbd> (3 listings) | [`TianggeListingPublisher.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeListingPublisher.java) publishes 3 listings (`P100`, `P200`, `P300`) mapped to LegacySupply SKUs (`XFM-7593`, `XFM-7477`, `XFM-7821`) on application startup. |
| **Stock Updates Follow Changes** | <kbd>**MET**</kbd> (0 late, 0 ignored) | [`TianggeStockSync.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeStockSync.java) and [`TianggeEventListener.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeEventListener.java) listen to domain events (`OrderPlacedEvent`, `OrderCancelledEvent`, `SupplierOrderDeliveredEvent`) and push updated stock snapshots via `PUT /stock` within 30 seconds without timers. |
| **Decisions Before Deadline** | <kbd>**MET**</kbd> (61 of 61 on time, 100%) | [`TianggeFeedPoller.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeFeedPoller.java) polls the feed every 2 seconds, and [`TianggeOrderProcessor.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeOrderProcessor.java) decides each order in < 2 seconds, well under the 60s deadline. |
| **No Oversold Orders** | <kbd>**MET**</kbd> (0 oversold orders) | [`OrderService.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/shop/OrderService.java) executes atomic all-or-nothing reservations. Orders exceeding available stock are never accepted. |
| **No Orders Rejected with Stock** | <kbd>**MET**</kbd> (0 rejected with stock) | Full pre-check against `InventoryService` ensures orders with sufficient inventory are immediately reserved and marked `ACCEPTED`. |
| **Repeated Orders Processed Once** | <kbd>**MET**</kbd> (9 repeated seen, 0 twice) | Two-tier deduplication via `TianggeProcessedEventRepository` (event ID level) and `TianggeOrderMappingRepository` (order ID level). Redeliveries are acknowledged without creating duplicate orders or decisions. |
| **Customer Cancellations** | <kbd>**MET**</kbd> (3 of 3 confirmed) | [`TianggeOrderProcessor.processOrderCancelled()`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeOrderProcessor.java) cancels the order via `OrderService.cancelOrder()`, restoring stock, confirming cancellation to Tiangge, and emitting a domain event for stock sync. |
| **Backorders Filled by Supplier** | <kbd>**MET**</kbd> (24 filled, 0 without PO) | When inventory is short, [`SupplierGatewayImpl.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/supplier/SupplierGatewayImpl.java) triggers replenishment. Orders are only marked `BACKORDERED` when an active PO exists. When delivered, [`TianggeBackorderManager.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/channel/TianggeBackorderManager.java) resolves them to `ACCEPTED`. |
| **Supplier Calls from App** | <kbd>**MET**</kbd> (414 calls, 27 POs) | All LegacySupply REST/XML communications carry the active client instance ID and occur via [`LegacySupplyClient.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/supplier/LegacySupplyClient.java) inside the monolith. |
| **Stage 4: Restart Test** | <kbd>**MET**</kbd> (Offline 1:49, 4 of 4 gap orders) | Application stopped for 109 seconds while 4 orders arrived. Restarted instance picked up feed cursor from database, processed all 4 gap orders, and did not re-handle prior orders. |
| **Stage 5: Hands-Off Test** | <kbd>**MET**</kbd> (100% unattended pass) | Evaluated over 10 consecutive minutes of rush orders. 32 of 32 orders decided on time, 0 oversells, 0 requests outside app, 100% uptime. Passed on Attempt 1. |

---

### 2. Architectural Boundaries & Encapsulation Rule Compliance

1. **Package-Private Channel Module (`edu.cit.verano.channel`):**
   - Verified by [`ChannelEncapsulationTest.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/test/java/edu/cit/verano/ChannelEncapsulationTest.java).
   - Only `ChannelService` and `ChannelStatus` are public.
   - All HTTP clients (`TianggeClient`), DTOs (`TianggeDtos`), workers (`TianggeFeedPoller`), managers (`TianggeBackorderManager`), and listeners (`TianggeEventListener`) are package-private.
2. **Order and Inventory Unaware of Tiangge:**
   - [`OrderService.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/shop/OrderService.java) and [`InventoryServiceImpl.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/inventory/InventoryServiceImpl.java) contain zero references to Tiangge, channel packages, or feed classes.
   - Channel interacts strictly through the standard domain contracts: calling `OrderService.placeOrder()` and `InventoryService.getItem()`, and responding to domain events (`OrderPlacedEvent`, `OrderCancelledEvent`, `SupplierOrderDeliveredEvent`).
3. **Environment-Configured Credentials:**
   - Client ID and API keys are read dynamically from environment variables (`LS_CLIENT_ID`, `LS_API_KEY`, `TIANGGE_CLIENT_ID`, `TIANGGE_API_KEY`) with fallback defaults for local evaluation.

---

### 3. Audit Note on Verification State

At `23:14:31`, a reset was executed on the verification portal specifically for the LegacySupply record (`scope: 'lab3'`). As documented on the verification portal (*"Your instructor sees what your record looked like before each reset"*), the portal permanently preserves the student's verified record prior to the reset, where every single check was 100% Met, including the completed Hands-Off test and Restart test. Following that reset, all LegacySupply checks were also brought to 100% Met through the implementation of automated catalog queries (`readCatalog`) and session renewal in [`LegacySupplyClient.java`](file:///c:/Users/marke/Downloads/modular-monolith-shop-main/modular-monolith-shop-main/backend/src/main/java/edu/cit/verano/supplier/LegacySupplyClient.java).
