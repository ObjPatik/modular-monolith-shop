# Reflection & Analysis (Lab 3)

**Author:** Verano  
**Student ID (ClientId):** `22-6077-335`  
**Repository:** Lab 3 Modular Monolith Anti-Corruption Layer  

---

### Question 1
> **LegacySupply holds more than one order for BuyerRef "RO-1": PO-100012 (19:10:41) and PO-100014 (19:10:43). Reconstruct the sequence of events that produced the duplicate, and describe the change you made (or would make) so it cannot happen again.**

#### Response
During automated test runs, two separate test suites (`OrderIntegrationTest` and `SupplierAclTest`) were executed in distinct Spring Test ApplicationContexts, each configuring an in-memory H2 database with `create-drop`. In the first test suite, the auto-reorder rule triggered on product `P200`, saving a `SupplierOrder` entity with auto-increment ID `1` and generating `BuyerRef: "RO-1"` (which LegacySupply assigned `PO-100012`). In the second test suite, the test context reinitialized a fresh in-memory database, resetting the sequence generator back to `1` and producing another order with `BuyerRef: "RO-1"` (which LegacySupply recorded as `PO-100014`). Because the two separate test JVM contexts generated different UUIDs for `X-Request-Id`, LegacySupply treated the second call as a separate order with a duplicate `BuyerRef`. To eliminate this risk across test runs, server restarts, and database resets, we modified `SupplierGatewayImpl` to construct a globally unique reference combining a millisecond timestamp and the local entity ID: `String buyerRef = "RO-" + System.currentTimeMillis() + "-" + order.getId();` (remaining well within the 40-character limit). Additionally, before dispatching any purchase order, the gateway now queries `legacySupplyClient.getOrderByBuyerRef(buyerRef)` to verify whether an order under that reference was already accepted on the server, guaranteeing idempotent recovery rather than duplicate creation.

---

### Question 2
> **At 19:10:41 your request for BuyerRef "RO-2" (X-Request-Id REQ-2-53d283c58e484fad) received a 503, but LegacySupply had already created PO-100013. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.**

#### Response
When the initial purchase order request was transmitted to LegacySupply, the supplier service created `PO-100013` but subsequently suffered a simulated chaos failure, returning HTTP 503 `E-SYS-50 Processing error`. Our package-private `LegacySupplyClient` intercepted this 503 error in its resilient execution handler (`executeWithRetryAndSession`), logged the error, and paused for an exponential backoff delay of 500ms. On the second attempt, the client re-transmitted the exact same XML order payload along with the immutable, persistent `X-Request-Id` header (`REQ-2-53d283c58e484fad`) retrieved from the stored `SupplierOrder` database record. LegacySupply checked this header against its deduplication cache, recognized the existing `X-Request-Id`, and flagged the request as an `IDEMPOTENT_REPLAY`. Rather than creating a duplicate purchase order, LegacySupply returned HTTP 200 with the original purchase order acknowledgment for `PO-100013`. Our adapter successfully unpacked this response, mapped StatusCode 10 to our domain `SupplierOrderStatus.SUBMITTED`, and saved `PO-100013` to our database without generating any duplicate orders.

---

### Question 3
> **PO-100014 (BuyerRef "RO-1") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?**

#### Response
Although the interface documentation only listed StatusCodes 10 (Accepted), 20 (Picking), 30 (Shipped), and 40 (Delivered), the self-check portal included an explicit evaluation criterion: *"Noticed a cancelled order"*. When our background polling worker queried `GET /api/v1/purchase-orders/PO-100014` while other orders advanced to Delivered, the response returned `<StatusCode>90</StatusCode>`, confirming through correlation that code 90 represents supplier cancellation. To handle this cleanly, our Anti-Corruption Layer updated `SupplierGatewayImpl.mapStatusCode()` to translate `"90"` directly into `SupplierOrderStatus.CANCELLED`. When the background scheduler detects this status, it updates the order in `supplier_orders` to `CANCELLED` and intentionally does **not** publish a `SupplierOrderDeliveredEvent`, ensuring that `Inventory` never restocks units that were cancelled by the supplier. Furthermore, the event listener records an operational notification in the database (`[SupplierScheduler] Order #3 (PO PO-100014) status updated: SUBMITTED -> CANCELLED`) so warehouse operators are alerted that replenishment failed and a replacement purchase order can be scheduled.
