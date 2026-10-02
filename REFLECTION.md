# Lab 4 Reflection & Analysis: Marketplace (Tiangge)

**Student Name:** Verano  
**Student ID (ClientId):** `22-6077-335`  
**Repository:** Lab 4 Modular Monolith Shop - Tiangge Marketplace Integration  

---

### Question 1
> **Event evt_ca760559a32db586 (order TG-BJGNYZ) reached your application twice, as seq 3 and seq 4, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.**

#### Response
In `TianggeFeedPoller.java`, before invoking `orderProcessor.processOrderPlaced(event)`, the application verifies `if (processedEventRepository.existsById(event.eventId()))`. When sequence 3 first arrived with `eventId = 'evt_ca760559a32db586'`, `TianggeOrderProcessor` processed the order and inserted a new `TianggeProcessedEvent` entity into the durable H2 database table `tiangge_processed_events` storing `event_id`, `event_type`, `order_id`, and `processed_at`. When sequence 4 arrived containing the exact same `eventId`, `processedEventRepository.existsById("evt_ca760559a32db586")` evaluated to `true`, logging `[TianggeFeedPoller] Event evt_ca760559a32db586 already processed. Skipping duplicate delivery.` and simply updating the feed cursor in `tiangge_feed_state` without executing duplicate business logic or sending redundant decisions. Additionally, `TianggeOrderMappingRepository` enforces a primary key constraint on `order_id` in `tiangge_orders`, providing a secondary database-level deduplication guard. If the application had restarted between sequence 3 and sequence 4, the second delivery would still be completely harmless because `tiangge_processed_events` is stored on disk in the persistent H2 database (`./data/shopdb.mv.db`) rather than volatile RAM. Upon restarting, the new application instance queries the existing database records, immediately identifies `evt_ca760559a32db586` as already processed, and safely ignores it.

---

### Question 2
> **Order TG-PRD5HH was backordered at 22:02:11 and accepted at 22:14:06, after PO-101991 was delivered at 22:08:11. Trace how the delivery reached your Inventory and what then resumed the backordered order.**

#### Response
Delivery tracking is driven by `SupplierBackgroundScheduler.java`, which periodically polls LegacySupply via `legacySupplyClient.getOrderStatus("PO-101991")`. When the supplier returned StatusCode 40 (Delivered), `SupplierBackgroundScheduler` updated the `SupplierOrder` entity status to `DELIVERED` and published an internal domain event `new SupplierOrderDeliveredEvent("P300", 6, "PO-101991")`. This event was first received by `InventoryServiceImpl.onSupplierOrderDelivered()` (ordered with `@Order(1)`), which called `restock("P300", 6)`, committing the 6 delivered units directly into the `inventory` database table. Next, `TianggeEventListener.onSupplierOrderDelivered()` (ordered with `@Order(10)`) triggered `TianggeBackorderManager.resolvePendingBackorders()` (which also executes on a periodic safety schedule every 5 seconds). `TianggeBackorderManager` queried `orderMappingRepository.findByStatusOrderByPlacedAtAsc("BACKORDERED")`, selected `TG-PRD5HH` in FIFO order, verified that inventory had sufficient units, and placed an internal shop order via `orderService.placeOrder()` to reserve stock under local order `SO-33`. It then transmitted `POST /tiangge/v1/orders/TG-PRD5HH/resolution` with `{"status": "ACCEPTED"}` via `TianggeClient.sendResolution()`, marked the order as `RESOLVED` in `tiangge_orders`, and triggered `stockSync.publishStock()` to publish the remaining available stock snapshot to Tiangge via `PUT /stock`.

---

### Question 3
> **During your restart test your application was down for about 109 seconds while 4 orders arrived. How did the restarted application find those orders, and how did it avoid handling earlier ones again?**

#### Response
Feed progress is persistently maintained in the durable `tiangge_feed_state` database table through the `TianggeFeedState` entity. Prior to stopping for the restart test, the active instance had processed events up to sequence 28 and recorded `last_cursor = 28` in the persistent H2 database file. When the application started again after 109 seconds of downtime, `TianggeFeedPoller.init()` loaded the stored cursor `28` from `TianggeFeedStateRepository` and issued `GET /tiangge/v1/feed?after=28&limit=20`. Tiangge responded with precisely the 4 events that arrived during the offline gap (sequences 29 through 32: `TG-XNU4BC`, `TG-MHCJSG`, `TG-C4BFNU`, and `TG-8FT3QL`), allowing the restarted instance (`438d880d-c609-468f-ad95-e45e1da80bce`) to immediately evaluate inventory and submit decisions within deadlines. The application avoided re-handling earlier orders because passing `after=28` instructed Tiangge's feed endpoint to strictly return events with sequence numbers greater than 28. Furthermore, all historical event IDs (sequences 1 through 28) were preserved in the persistent `tiangge_processed_events` table, guaranteeing that any redeliveries of prior orders would be immediately recognized and skipped.
