# Modular Monolith Integration (Lab 2)
### Multi-Item Orders, Cancellation & Restock, In-Monolith Domain Events, and Notifications

A production-grade Modular Monolith extending Lab 1 with four integration patterns:
1. **Module-to-Module In-Process Integration**: Strict package-private boundary between `Order` and `Inventory` modules within the same JVM.
2. **In-Process Publish/Subscribe Domain Events**: `OrderService` and `InventoryService` publish domain events (`OrderPlacedEvent`, `OrderRejectedEvent`, `LowStockEvent`) consumed by a dedicated `Notification` module.
3. **Service-to-Database Integration**: Spring Data JPA connecting to a shared cloud PostgreSQL database hosted on **Supabase** (with credential isolation).
4. **Client-to-Service Integration**: Modern **React + Vite** single-page application featuring a shopping cart, live inventory dashboard, cancellation controls, and real-time activity feed.

---

## 🏛️ System Architecture & Module Boundaries

```
                                  +---------------------------------------------------+
                                  |                 React Web Client                  |
                                  |       (Cart + Live Inventory + Feed @ :5173)      |
                                  +-------------------------+-------------------------+
                                                            | HTTP REST (CORS)
                                                            v
+-----------------------------------------------------------------------------------------------------------------------+
| Spring Boot Application (edu.cit.verano)                                                                              |
|                                                                                                                       |
|   +---------------------------------------+                                                                           |
|   | Order Module (edu.cit.verano.shop)    |                                                                           |
|   |   - OrderController                   |                                                                           |
|   |   - OrderService                      |                                                                           |
|   |   - Order & OrderItem (JPA Entities)  |                                                                           |
|   +-------------------+-------------------+                                                                           |
|                       |                                                                                               |
|      (1) In-Process   |                           (3) Spring ApplicationEventPublisher                                |
|          Pre-Check,   |                               - OrderPlacedEvent / OrderRejectedEvent                         |
|          Reserve &    |                               - LowStockEvent                                                 |
|          Restock      |                                           |                                                   |
|                       v                                           v                                                   |
|   +---------------------------------------+   +-------------------------------------------------------------------+   |
|   | Inventory Module                      |   | Notification Module (edu.cit.verano.notification)                 |   |
|   | (edu.cit.verano.inventory)            |   |   - NotificationEventListener (@EventListener)                    |   |
|   |   - InventoryService (interface)      |   |   - Notification (JPA Entity) & Repository                        |   |
|   |   - InventoryServiceImpl              |   |   - NotificationController (GET /api/notifications)               |   |
|   |     (PACKAGE-PRIVATE: boundary)       |   +-------------------------------------------------------------------+   |
|   |   - InventoryItem (JPA Entity)        |                                                                           |
|   +---------------------------------------+                                                                           |
+---------------------------------------------------+-------------------------------------------------------------------+
                                                    | JPA / JDBC (SSL)
                                                    v
                                      +---------------------------+
                                      |   Supabase (PostgreSQL)   |
                                      |   - inventory                 |
                                      |   - orders & order_items      |
                                      |   - notifications             |
                                      +---------------------------+
```

### Module Boundary Rules
1. **Order -> Inventory**: `OrderService` may depend **only** on the `InventoryService` interface via constructor injection. `InventoryServiceImpl` remains strictly **package-private** (`class InventoryServiceImpl implements InventoryService`).
2. **Order / Inventory -> Notification**: Neither `Order` nor `Inventory` imports or references anything from `edu.cit.verano.notification`.
3. **Notification -> Order / Inventory**: `Notification` module depends **only on event classes** (`edu.cit.verano.events.*`). It never calls `OrderService` or `InventoryService`.

---

## 📦 Project Structure

```
Monolith Integration/
├── .env.example                          # Template for Supabase environment variables
├── .gitignore                            # Keeps credentials (.env) and build artifacts out of Git
├── supabase_setup.sql                    # SQL schema for inventory, orders, order_items, notifications
├── README.md                             # Architecture, setup, evidence, and reflection
├── backend/                              # Spring Boot 3.2 (Java 21) Modular Monolith
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/
│       │   ├── java/edu/cit/verano/
│       │   │   ├── ShopApplication.java      # Main entry point (@SpringBootApplication)
│       │   │   ├── events/                   # Shared In-Process Domain Events
│       │   │   │   ├── OrderPlacedEvent.java
│       │   │   │   ├── OrderRejectedEvent.java
│       │   │   │   ├── LowStockEvent.java
│       │   │   │   └── OrderItemEventDto.java
│       │   │   ├── inventory/                # INVENTORY MODULE
│       │   │   │   ├── InventoryItem.java
│       │   │   │   ├── InventoryItemDto.java
│       │   │   │   ├── InventoryRepository.java  # Package-private repository
│       │   │   │   ├── InventoryService.java     # Public interface (getItem, reserve, restock)
│       │   │   │   ├── InventoryServiceImpl.java # Package-private implementation
│       │   │   │   └── InventorySeeder.java      # Baseline product replenishment seeder
│       │   │   ├── shop/                     # ORDER MODULE
│       │   │   │   ├── Order.java            # Order entity (supports CONFIRMED, REJECTED, CANCELLED)
│       │   │   │   ├── OrderItem.java        # Order line item entity
│       │   │   │   ├── OrderRepository.java
│       │   │   │   ├── OrderRequest.java     # Multi-item order request
│       │   │   │   ├── OrderResponse.java    # Order response with item outcomes
│       │   │   │   ├── OrderService.java     # Multi-item rollback, cancellation, event publisher
│       │   │   │   └── OrderController.java  # REST API (/orders, /orders/{id}/cancel, /inventory)
│       │   │   └── notification/             # NOTIFICATION MODULE
│       │   │       ├── Notification.java     # Notification entity
│       │   │       ├── NotificationRepository.java
│       │   │       ├── NotificationEventListener.java # Listens to domain events
│       │   │       └── NotificationController.java    # REST API (/notifications)
│       │   └── resources/
│       │       └── application.properties    # Environment-driven database configuration
│       └── test/java/edu/cit/verano/
│           └── OrderIntegrationTest.java     # Full suite testing multi-item, rollback, cancel, events
└── frontend/                             # React + Vite Client
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── App.jsx                       # Cart, live inventory, cancellation, and activity feed
        ├── index.css                     # Modern dark styling with low-stock alerts
        └── main.jsx
```

---

## 🚀 Supabase Database Setup Steps

1. Log into your project at [supabase.com](https://supabase.com).
2. Open the **SQL Editor** on the left menu.
3. Paste the contents of [`supabase_setup.sql`](file:///c:/Users/L23Y19W42/Downloads/Monolith%20Integration/supabase_setup.sql) and click **Run**:
   ```sql
   -- 1. Create Inventory Table
   CREATE TABLE IF NOT EXISTS inventory (
       product_id VARCHAR(50) PRIMARY KEY,
       name VARCHAR(255) NOT NULL,
       stock INT NOT NULL CHECK (stock >= 0)
   );

   -- 2. Seed Baseline Products
   INSERT INTO inventory (product_id, name, stock) VALUES
       ('P100', 'Wireless Mouse', 25),
       ('P200', 'Mechanical Keyboard', 10),
       ('P300', 'USB-C Hub', 0)
   ON CONFLICT (product_id) DO UPDATE 
   SET name = EXCLUDED.name, stock = EXCLUDED.stock;

   -- 3. Create Orders Table
   CREATE TABLE IF NOT EXISTS orders (
       order_id BIGSERIAL PRIMARY KEY,
       status VARCHAR(20) NOT NULL,
       reason VARCHAR(255),
       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
   );

   -- 4. Create Order Items Table
   CREATE TABLE IF NOT EXISTS order_items (
       item_id BIGSERIAL PRIMARY KEY,
       order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
       product_id VARCHAR(50) NOT NULL REFERENCES inventory(product_id),
       quantity INT NOT NULL CHECK (quantity > 0)
   );

   -- 5. Create Notifications Table
   CREATE TABLE IF NOT EXISTS notifications (
       notification_id BIGSERIAL PRIMARY KEY,
       message VARCHAR(500) NOT NULL,
       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
   );
   ```
4. Configure your `.env` file in the project root:
   ```properties
   SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require
   SPRING_DATASOURCE_USERNAME=postgres.ixnjsyjctrdcjvculmxq
   SPRING_DATASOURCE_PASSWORD=your_actual_password
   ```

---

## 💻 Running the Application

### 1. Run Automated Test Suite
```powershell
cd backend
.\mvnw.cmd clean test
```
*All 6 tests verify:*
- `InventoryServiceImpl` package-private boundary enforcement
- Multi-item confirmed orders
- All-or-nothing rollback (zero stock deducted when one item fails)
- Order cancellation and inventory restocking
- Cancellation HTTP 404 and HTTP 409 conflict handling
- Low-stock auto-reorder domain event publishing

### 2. Start the Backend
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
Starts at `http://localhost:8080`.

### 3. Start the Frontend
```powershell
cd frontend
npm run dev
```
Starts at `http://localhost:5173`.

---

## 📸 Network Tab Evidence

### Scenario 1: Multi-Item Order (All Items Succeed — CONFIRMED)
- **Request URL**: `http://localhost:8080/api/orders`
- **HTTP Method**: `POST`
- **Status Code**: `200 OK`
- **Request Payload**:
  ```json
  {
    "items": [
      { "productId": "P100", "quantity": 2 },
      { "productId": "P200", "quantity": 1 }
    ]
  }
  ```
- **Response Payload**:
  ```json
  {
    "orderId": 12,
    "status": "CONFIRMED",
    "reason": null,
    "items": [
      { "productId": "P100", "quantity": 2, "outcome": "RESERVED" },
      { "productId": "P200", "quantity": 1, "outcome": "RESERVED" }
    ],
    "inventory": [
      { "productId": "P100", "name": "Wireless Mouse", "stock": 19 },
      { "productId": "P200", "name": "Mechanical Keyboard", "stock": 9 },
      { "productId": "P300", "name": "USB-C Hub", "stock": 0 }
    ]
  }
  ```
- **Database Effect**: Stock for `P100` decremented by 2; stock for `P200` decremented by 1. Order #12 saved with 2 `order_items`. `NotificationEventListener` logged `"Order #12 confirmed with 2 line item(s)"`.

---

### Scenario 2: Multi-Item Order (One Item Fails — All-or-Nothing REJECTED)
- **Request URL**: `http://localhost:8080/api/orders`
- **HTTP Method**: `POST`
- **Status Code**: `200 OK`
- **Request Payload**:
  ```json
  {
    "items": [
      { "productId": "P100", "quantity": 1 },
      { "productId": "P300", "quantity": 1 }
    ]
  }
  ```
- **Response Payload**:
  ```json
  {
    "orderId": 13,
    "status": "REJECTED",
    "reason": "Requested quantity (1) for 'USB-C Hub' (P300) exceeds available stock (0)",
    "items": [
      { "productId": "P100", "quantity": 1, "outcome": "REJECTED_NO_STOCK" },
      { "productId": "P300", "quantity": 1, "outcome": "REJECTED_NO_STOCK" }
    ],
    "inventory": [
      { "productId": "P100", "name": "Wireless Mouse", "stock": 19 },
      { "productId": "P200", "name": "Mechanical Keyboard", "stock": 9 },
      { "productId": "P300", "name": "USB-C Hub", "stock": 0 }
    ]
  }
  ```
- **Database Effect**: `P100` stock remained unchanged (19 units) — **zero partial reservation**. Order #13 saved as `REJECTED`. `OrderRejectedEvent` published to `notifications`.

---

### Scenario 3: Order Cancellation & Inventory Restock
- **Request URL**: `http://localhost:8080/api/orders/12/cancel`
- **HTTP Method**: `POST`
- **Status Code**: `200 OK` (Duplicate cancel returns `409 Conflict`)
- **Response Payload**:
  ```json
  {
    "orderId": 12,
    "status": "CANCELLED",
    "reason": "Order cancelled by customer. Inventory restocked.",
    "items": [
      { "itemId": 1, "productId": "P100", "quantity": 2 },
      { "itemId": 2, "productId": "P200", "quantity": 1 }
    ]
  }
  ```
- **Verified via `GET /api/inventory`**:
  ```json
  [
    { "productId": "P100", "name": "Wireless Mouse", "stock": 21 },
    { "productId": "P200", "name": "Mechanical Keyboard", "stock": 10 },
    { "productId": "P300", "name": "USB-C Hub", "stock": 0 }
  ]
  ```
- **Database Effect**: Order status updated to `CANCELLED`. Stock for `P100` restored from 19 -> 21; stock for `P200` restored from 9 -> 10.

---

### Scenario 4: Notification Feed & Low-Stock Auto-Reorder Alert
- **Trigger**: Order #14 requested 7 units of `P200`, reducing remaining stock to 3 (below threshold of 5).
- **Request URL**: `http://localhost:8080/api/notifications`
- **HTTP Method**: `GET`
- **Status Code**: `200 OK`
- **Response Payload**:
  ```json
  [
    {
      "notificationId": 4,
      "message": "Order #14 confirmed with 1 line item(s)",
      "createdAt": "2026-09-17T10:25:45.946404Z"
    },
    {
      "notificationId": 3,
      "message": "REORDER NEEDED: Low stock alert for 'Mechanical Keyboard' (P200) - remaining stock: 3 (threshold: 5)",
      "createdAt": "2026-09-17T10:25:45.651660Z"
    },
    {
      "notificationId": 2,
      "message": "Order #13 rejected: Requested quantity (1) for 'USB-C Hub' (P300) exceeds available stock (0)",
      "createdAt": "2026-09-17T10:25:44.350293Z"
    },
    {
      "notificationId": 1,
      "message": "Order #12 confirmed with 2 line item(s)",
      "createdAt": "2026-09-17T10:25:43.352734Z"
    }
  ]
  ```

---

## ⚡ Synchronous vs. Asynchronous Event Listeners Note
In this lab, event listeners on `NotificationEventListener` run **synchronously** within the transaction boundary by default.
- **Why Synchronous**: Synchronous listeners ensure immediate consistency and simplicity. The notification entry is written and visible immediately when the frontend re-queries `/api/notifications`.
- **When `@Async` Would Be Used**: If notification delivery involved external operations (such as sending emails via SMTP, sending SMS via Twilio, or pushing webhooks), making the listener `@Async` prevents slow third-party I/O from blocking order completion. However, `@Async` listeners execute in a separate thread, requiring careful error handling so that notification failures do not unintentionally roll back confirmed orders.

---

## 📝 Reflection

### 1. Multi-Item Order Atomicity: In-Process vs. Across a Network
In our modular monolith, multi-item orders touch `InventoryService` multiple times during a single user request. **In-process execution guarantees atomicity through two unified layers:**
1. **Application-Level Pre-Validation**: `OrderService` executes an all-or-nothing pre-validation pass across all requested line items before mutating state. If even one product has insufficient stock, the entire request is rejected and zero items are reserved.
2. **Database-Level ACID Transaction**: The entire method runs under Spring's `@Transactional`. Because both `Order` and `Inventory` share the same database connection and thread context, Hibernate coordinates all reads, stock decrements, and order inserts within a single local PostgreSQL transaction. If an unexpected runtime exception occurs at any point, PostgreSQL rolls back all row modifications automatically.

**If Order and Inventory were split across a network as separate microservices, local ACID transactions would be lost.** To maintain consistency, we would need to add:
- **Saga Orchestration with Compensating Transactions**: If reserving item 1 succeeds over HTTP but reserving item 2 fails, the Order service must orchestrate compensating calls (e.g., `POST /api/inventory/{id}/unreserve` or `restock`) to reverse previous reservations.
- **Two-Phase Commit (2PC) or Distributed Locks**: Coordination protocols like WS-AtomicTransaction or distributed locks (e.g., Redis Redlock) to prevent race conditions across service boundaries, though at severe latency and availability costs.
- **Idempotency Keys**: Network retries during reservations require idempotency keys so that duplicated packets do not accidentally deduct stock multiple times.

### 2. Event-Driven Decoupling vs. Direct Method Invocation
Publishing domain events (`OrderPlacedEvent`, `OrderRejectedEvent`, `LowStockEvent`) via Spring's `ApplicationEventPublisher` fundamentally transforms the relationship between `OrderService` and `Notification`:
- **Loose Coupling & Open-Closed Principle**: `OrderService` has zero knowledge of `Notification`. It simply broadcasts that a business state change occurred. New consumers (e.g., Analytics, Audit Logging, Recommendation engines) can be added without modifying a single line of `OrderService` code.
- **Failure Isolation**: The Order module does not depend on notification schemas, repositories, or services.

**If Notification became a separate microservice**, in-memory events would no longer suffice. We would need:
- **A Message Broker**: An asynchronous publish-subscribe broker (e.g., Apache Kafka, RabbitMQ, or AWS SNS/SQS) with topic partitions and consumer groups.
- **Transactional Outbox Pattern**: To prevent lost events if the application crashes between the database commit and the broker publication, events must first be saved to an `outbox` table within the order transaction, then dispatched to Kafka by a change-data-capture process (like Debezium).
- **At-Least-Once Delivery & Consumer Idempotency**: Because distributed message brokers deliver messages at-least-once, the Notification microservice must implement idempotent consumers to prevent duplicate notification logs if an event is redelivered.

### 3. Service Extraction: Which Module to Extract First and What Changes
If forced to extract exactly one module into its own microservice first, **the Notification module is the clear and unequivocal choice.**

#### Why Notification First:
1. **Asynchronous & Non-Critical Path**: Notification processing is purely reactive. If the notification service experiences downtime or high latency, customer orders can still be accepted and confirmed without interruption.
2. **Zero Inbound Dependencies**: No other module in the system ever calls Notification or depends on its synchronous response.
3. **Natural Event Boundary**: The Notification module already interacts exclusively via clean domain events, making the transition to an external message broker trivial.

#### Code Changes Required for Extraction:
1. **Replace Spring Event Publisher with Message Broker Producer**: Update the event publishing code in `ShopApplication` / `OrderService` to serialize `OrderPlacedEvent` and `LowStockEvent` as JSON to a Kafka or RabbitMQ topic.
2. **Standalone Notification Deployable**: Move `edu.cit.verano.notification` into its own Git repository and Spring Boot project with its own PostgreSQL / MongoDB database (`notifications` table).
3. **Kafka Consumer**: In the new service, replace `@EventListener` with `@KafkaListener(topics = "order-events")`.
4. **Remove Notification Endpoints from Monolith**: Route frontend requests for `/api/notifications` to the new notification service directly or via an API Gateway.
