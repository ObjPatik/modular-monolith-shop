-- ==============================================================================
-- Supabase (PostgreSQL) Database Initialization Script
-- Lab 2: Modular Monolith Integration (Multi-Item, Cancellation, Events, Notifications)
-- ==============================================================================

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
SET name = EXCLUDED.name, 
    stock = EXCLUDED.stock;

-- 3. Create Orders Table (Supports CONFIRMED, REJECTED, CANCELLED)
CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Create Order Items Table (Multi-Item support with Cascade Delete)
CREATE TABLE IF NOT EXISTS order_items (
    item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL REFERENCES inventory(product_id),
    quantity INT NOT NULL CHECK (quantity > 0)
);

-- 5. Create Notifications Table (Domain Event logging)
CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Create Supplier Orders Table (Anti-Corruption Layer - Lab 3)
CREATE TABLE IF NOT EXISTS supplier_orders (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL REFERENCES inventory(product_id),
    buyer_ref VARCHAR(40) NOT NULL UNIQUE,
    request_id VARCHAR(80) NOT NULL UNIQUE,
    po_number VARCHAR(50),
    cases INT NOT NULL,
    units INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Quick inspection queries:
-- SELECT * FROM inventory;
-- SELECT * FROM orders ORDER BY created_at DESC;
-- SELECT * FROM order_items;
-- SELECT * FROM notifications ORDER BY created_at DESC;
-- SELECT * FROM supplier_orders ORDER BY created_at DESC;
