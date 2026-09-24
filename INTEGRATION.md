# LegacySupply Integration Specification & Contract Discovery (Lab 3)

**Author:** Verano  
**Student ID (ClientId):** `22-6077-335`  
**Base URL:** `https://legacysupply.onrender.com/api/v1`  

---

## 1. Inventory to Supplier Product Mapping

The following mapping links internal inventory items in the shop with LegacySupply catalog items retrieved via `GET /api/v1/catalog`:

| Internal Product ID | Internal Product Name | LegacySupply `SupplierSku` | Supplier Description | PackSize | Supplier Unit Cost |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`P100`** | Wireless Mouse | `XFM-7477` | WIRELESS MOUSE 2.4GHZ | **10** | 450.00 PHP |
| **`P200`** | Mechanical Keyboard | `XFM-7593` | KEYBOARD MECH TKL | **24** | 1899.00 PHP |
| **`P300`** | USB-C Hub | `XFM-3161` | USB HUB 4-PORT | **6** | 399.00 PHP |

---

## 2. LegacySupply Session Mechanism & Measured Lifespan

### How Sessions Work
1. **Authentication:** The client sends an XML authentication document to `POST /api/v1/auth/token`:
   ```xml
   <AuthRequest>
     <ClientId>22-6077-335</ClientId>
     <ApiKey>LSK-XXXXXXXXXXXXXXXXXXXX</ApiKey>
   </AuthRequest>
   ```
2. **Session Token Response:** LegacySupply returns:
   ```xml
   <AuthResponse>
     <SessionToken>b5fae3f3a31d80dbe483eb1abc6ac698b1cf</SessionToken>
     <IssuedAt>2026-09-24T10:56:34.112Z</IssuedAt>
   </AuthResponse>
   ```
3. **Usage:** The returned `SessionToken` must be passed in the `X-LS-Session` HTTP header on all subsequent requests (except `POST /auth/token` and `GET /ping`).
4. **Expiration & Re-authentication:** When a session token expires, LegacySupply returns HTTP `401 Unauthorized` with XML error body `<LSError><Code>E-AUTH-07</Code><Message>Session not valid.</Message></LSError>`.
5. **Adapter Strategy:** The Anti-Corruption Layer caches the session token in memory. If a request encounters `401` with `E-AUTH-07` (or `E-AUTH-02` / `E-AUTH-03`), the adapter intercepts the response, immediately requests a new session token, and transparently replays the original request.

### Measured Lifespan
- **Measured Session Lifetime:** Approximately **180 seconds (3 minutes)**.
- **Evidence from Probing Logs:**
  - Token issued at: `10:56:34 UTC`
  - Successful requests executed until: `~10:59:34 UTC`
  - Attempt at `11:00:01 UTC` failed with `401 Unauthorized` (`E-AUTH-07 Session not valid`).
  - Total valid duration: 207 seconds ($\approx 3$ minutes).

---

## 3. Error Codes Observed & Causes

| Error Code | HTTP Status | Message | Root Cause & Reproduction Scenario |
| :--- | :--- | :--- | :--- |
| **`E-AUTH-01`** | 401 | Credentials rejected. | Sent invalid `ClientId` or wrong `ApiKey` in `<AuthRequest>`. |
| **`E-AUTH-02`** | 401 | Session header missing. | Sent an authenticated endpoint request without the `X-LS-Session` header. |
| **`E-AUTH-03`** | 401 | Session not recognized. | Sent a malformed or fabricated `X-LS-Session` token. |
| **`E-AUTH-07`** | 401 | Session not valid. | Sent a request after the token expired ($\ge 3$ minutes since issue). |
| **`E-FMT-01`** | 415 | Unsupported media. | Sent `Content-Type: application/json` or missing XML header. LegacySupply requires `application/xml`. |
| **`E-FMT-02`** | 400 | Malformed document. | Sent invalid XML syntax or missing required XML root tags. |
| **`E-REF-05`** | 400 | BuyerRef invalid. | Sent a purchase order with `BuyerRef` empty or exceeding 40 characters. |
| **`E-SKU-02`** | 422 | Item not recognized. | Sent a `SupplierSku` that does not exist in the partner's catalog (e.g. `UNKNOWN-9999`). |
| **`E-QTY-11`** | 422 | Quantity invalid. | Sent `Qty` $< 1$, $> 99$, or non-integer value. |
| **`E-IDEM-04`** | 409 | Request id reused with different content. | Sent the same `X-Request-Id` with different `SupplierSku`, `Qty`, or `BuyerRef`. |
| **`E-PO-04`** | 404 | Order not found. | Queried `GET /purchase-orders/{PoNumber}` with a non-existent PO number. |
| **`E-QRY-06`** | 400 | Query parameter required. | Called `GET /purchase-orders` without the required `buyerRef` parameter. |
| **`E-RATE-03`** | 429 | Request quota exceeded. | Polled or sent requests too quickly, violating LegacySupply's rate limit. |
| **`E-SYS-50`** | 503 | Processing error. | Transient supplier internal error. Requires retry with exponential backoff. |
| **`E-SYS-99`** | 503 | Service unavailable. Try later. | Planned supplier outage or chaos injection. Order marked `PENDING` for background replay. |

---

## 4. Qty and Uom Semantics & Worked Example

### Definitions in Our Own Words
- **`Uom` (Unit of Measure):** LegacySupply orders in cases (`CS`), not individual consumer units (`EA` or `UNITS`). One case contains `PackSize` units.
- **`Qty`:** The integer number of cases (`CS`) to order from LegacySupply, which must be a whole number between 1 and 99.
- **Conversion Rule:** Because LegacySupply only sells in full cases, our Anti-Corruption Layer must convert the internal inventory units needed into supplier cases by **rounding up** to the next whole case:
  $$\text{cases} = \lceil \frac{\text{unitsNeeded}}{\text{PackSize}} \rceil = \text{Math.ceil}\left(\frac{\text{unitsNeeded}}{\text{PackSize}}\right)$$
  $$\text{unitsReceived} = \text{cases} \times \text{PackSize}$$

### Worked Arithmetic Example
- Product: **`P200` (Mechanical Keyboard)**
- Internal Inventory Needed: **5 units**
- Supplier SKU: `XFM-7593`
- Supplier `PackSize`: **24 units per case**
- Calculation:
  $$\text{Qty (Cases)} = \lceil 5 / 24 \rceil = 1 \text{ case}$$
- Purchase Order XML Sent:
  ```xml
  <PurchaseOrder>
    <SupplierSku>XFM-7593</SupplierSku>
    <Qty>1</Qty>
    <BuyerRef>RO-101</BuyerRef>
  </PurchaseOrder>
  ```
- Supplier Acknowledgment: Returns `Qty = 1`, `Uom = CS`.
- Restock on Delivery:
  When status transitions to `40` (Delivered), Inventory receives:
  $$1 \text{ case} \times 24 \text{ units/case} = 24 \text{ units}$$
  Inventory stock is incremented by **24 units** (fulfilling the 5 needed units, with 19 excess units in buffer stock).

---

## 5. Handling Unexpected & Cancelled Supplier Statuses

LegacySupply defines the following order status codes:
- `10`: **Accepted** $\rightarrow$ mapped to `SUBMITTED`
- `20`: **Picking** $\rightarrow$ mapped to `PICKING`
- `30`: **Shipped** $\rightarrow$ mapped to `SHIPPED`
- `40`: **Delivered** $\rightarrow$ mapped to `DELIVERED` (triggers `restock` in Inventory)
- `90`: **Cancelled** $\rightarrow$ mapped to `CANCELLED` (order cancelled by supplier)

### Handling Cancelled Orders
- If LegacySupply cancels an order (or returns a cancelled status), the ACL maps this to internal status `CANCELLED`.
- It logs a warning: `[SupplierACL] Purchase order PO-xxxx was cancelled by supplier`.
- It does **not** restock inventory (since no goods were received).
- It creates an alert / notification so operations can investigate or trigger a replacement reorder.

### Handling Unknown / Unexpected Statuses
- If LegacySupply returns an unrecognized status code (e.g., `50`, `99`, or non-numeric):
  1. The system logs an `ERROR` with the raw payload: `[SupplierACL] Unknown status code received from supplier: {code}`.
  2. The order status in `supplier_orders` is updated to `UNKNOWN_STATUS` without throwing an unhandled exception or breaking the poller loop.
  3. The scheduled poller continues monitoring other active orders.
