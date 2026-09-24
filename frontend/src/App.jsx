import React, { useState, useEffect } from 'react';

const API_BASE_URL = 'http://localhost:8080/api';

export default function App() {
  const [products, setProducts] = useState([
    { productId: 'P100', name: 'Wireless Mouse', stock: 25 },
    { productId: 'P200', name: 'Mechanical Keyboard', stock: 10 },
    { productId: 'P300', name: 'USB-C Hub', stock: 0 }
  ]);

  // Cart state: array of { productId, name, quantity, stock }
  const [cart, setCart] = useState([]);
  const [selectedProductId, setSelectedProductId] = useState('P100');
  const [quantity, setQuantity] = useState(1);

  const [loading, setLoading] = useState(false);
  const [orderResult, setOrderResult] = useState(null);
  const [recentOrders, setRecentOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [apiError, setApiError] = useState(null);
  const [lastPayloads, setLastPayloads] = useState(null);

  // Fetch latest inventory from backend
  const fetchInventory = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/inventory`);
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          setProducts(data);
        }
      }
    } catch (err) {
      console.warn('Could not connect to backend inventory:', err.message);
    }
  };

  // Fetch recent orders
  const fetchOrders = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/orders`);
      if (res.ok) {
        const data = await res.json();
        setRecentOrders(data);
      }
    } catch (err) {
      console.warn('Could not connect to backend orders:', err.message);
    }
  };

  // Fetch domain event notifications
  const fetchNotifications = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/notifications`);
      if (res.ok) {
        const data = await res.json();
        setNotifications(data);
      }
    } catch (err) {
      console.warn('Could not connect to backend notifications:', err.message);
    }
  };

  const refreshAll = () => {
    fetchInventory();
    fetchOrders();
    fetchNotifications();
  };

  useEffect(() => {
    refreshAll();
  }, []);

  // Add selected item to Cart
  const handleAddToCart = (e) => {
    e.preventDefault();
    const product = products.find((p) => p.productId === selectedProductId);
    if (!product) return;

    setCart((prevCart) => {
      const existingIdx = prevCart.findIndex((i) => i.productId === selectedProductId);
      if (existingIdx >= 0) {
        const updated = [...prevCart];
        updated[existingIdx].quantity += quantity;
        return updated;
      } else {
        return [...prevCart, { productId: product.productId, name: product.name, quantity, stock: product.stock }];
      }
    });
  };

  // Remove item from Cart
  const handleRemoveFromCart = (productId) => {
    setCart((prev) => prev.filter((item) => item.productId !== productId));
  };

  // Submit Multi-Item Order
  const handleSubmitOrder = async () => {
    if (cart.length === 0) return;

    setLoading(true);
    setApiError(null);

    const requestPayload = {
      items: cart.map((item) => ({
        productId: item.productId,
        quantity: item.quantity
      }))
    };

    try {
      const startTime = performance.now();
      const res = await fetch(`${API_BASE_URL}/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestPayload)
      });

      const responsePayload = await res.json();
      const durationMs = Math.round(performance.now() - startTime);

      setOrderResult(responsePayload);
      setLastPayloads({
        scenario: 'Multi-Item Order',
        url: `${API_BASE_URL}/orders`,
        method: 'POST',
        request: requestPayload,
        response: responsePayload,
        statusCode: res.status,
        durationMs
      });

      if (responsePayload.status === 'CONFIRMED') {
        setCart([]); // clear cart on successful order
      }

      refreshAll();
    } catch (err) {
      setApiError(`Failed to submit order: ${err.message}. Is backend running?`);
    } finally {
      setLoading(false);
    }
  };

  // Cancel Order & Restock
  const handleCancelOrder = async (orderId) => {
    if (!window.confirm(`Are you sure you want to cancel Order #${orderId}? Reserved items will be returned to stock.`)) {
      return;
    }

    setLoading(true);
    setApiError(null);

    try {
      const startTime = performance.now();
      const res = await fetch(`${API_BASE_URL}/orders/${orderId}/cancel`, {
        method: 'POST'
      });

      const responsePayload = await res.json();
      const durationMs = Math.round(performance.now() - startTime);

      setLastPayloads({
        scenario: `Cancel Order #${orderId}`,
        url: `${API_BASE_URL}/orders/${orderId}/cancel`,
        method: 'POST',
        request: {},
        response: responsePayload,
        statusCode: res.status,
        durationMs
      });

      refreshAll();
    } catch (err) {
      setApiError(`Failed to cancel order: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container">
      <header>
        <h1>Modular Monolith Store</h1>
        <p>Order, Inventory & Notification In-Process Integration with Supabase PostgreSQL</p>
        <div>
          <span className="badge badge-arch">Lab 2: Multi-Item Orders • Cancellation • Domain Events</span>
        </div>
      </header>

      {apiError && <div className="error-banner">⚠️ {apiError}</div>}

      <div className="grid-layout">
        {/* Left Column: Multi-Item Cart & Order Placement */}
        <section className="card">
          <h2>
            <span>Shopping Cart & Order</span>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              {cart.length} item(s) in cart
            </span>
          </h2>

          {/* Add Item to Cart Form */}
          <form onSubmit={handleAddToCart}>
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr auto', gap: '0.75rem', alignItems: 'flex-end' }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="product-select">Product</label>
                <select
                  id="product-select"
                  className="form-control"
                  value={selectedProductId}
                  onChange={(e) => setSelectedProductId(e.target.value)}
                  disabled={loading}
                >
                  {products.map((item) => (
                    <option key={item.productId} value={item.productId}>
                      {item.productId} — {item.name} ({item.stock} left)
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="quantity-input">Quantity</label>
                <input
                  id="quantity-input"
                  type="number"
                  min="1"
                  max="999"
                  className="form-control"
                  value={quantity}
                  onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                  disabled={loading}
                />
              </div>

              <button type="submit" className="btn" disabled={loading}>
                + Add
              </button>
            </div>
          </form>

          {/* Current Cart */}
          <div className="cart-box">
            <h3 style={{ fontSize: '0.9rem', marginBottom: '0.5rem', color: 'var(--text-muted)' }}>Cart Items</h3>
            {cart.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Cart is empty. Add products above to build a multi-item order.</p>
            ) : (
              <table className="cart-table">
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Qty</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {cart.map((item) => (
                    <tr key={item.productId}>
                      <td>{item.name} ({item.productId})</td>
                      <td><strong>{item.quantity}</strong></td>
                      <td>
                        <button
                          type="button"
                          className="btn-remove"
                          onClick={() => handleRemoveFromCart(item.productId)}
                        >
                          ✕ Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <button
            type="button"
            className="btn btn-block"
            onClick={handleSubmitOrder}
            disabled={loading || cart.length === 0}
          >
            {loading ? 'Processing Multi-Item Order...' : `Submit Order (${cart.length} items)`}
          </button>

          {/* Order Placement Result */}
          {orderResult && (
            <div className={`result-box ${orderResult.status.toLowerCase()}`}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <strong>Order Outcome: #{orderResult.orderId || 'N/A'}</strong>
                <span className={`status-tag ${orderResult.status.toLowerCase()}`}>{orderResult.status}</span>
              </div>

              {orderResult.status === 'CONFIRMED' ? (
                <div>
                  <p style={{ fontSize: '0.85rem' }}>✅ All items verified and reserved atomically!</p>
                  {orderResult.items && (
                    <ul style={{ fontSize: '0.8rem', paddingLeft: '1.2rem', marginTop: '0.4rem' }}>
                      {orderResult.items.map((i, idx) => (
                        <li key={idx}>{i.productId}: {i.quantity} unit(s) — <strong>{i.outcome}</strong></li>
                      ))}
                    </ul>
                  )}
                </div>
              ) : (
                <div>
                  <p style={{ fontSize: '0.85rem', color: '#fca5a5' }}>
                    ❌ <strong>All-or-Nothing Rollback:</strong> {orderResult.reason}
                  </p>
                  <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                    Zero items were deducted. Entire transaction aborted.
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Network Tab Evidence Helper */}
          {lastPayloads && (
            <details className="evidence-box" open>
              <summary>📋 Network Tab Evidence ({lastPayloads.scenario})</summary>
              <pre>
{`// HTTP ${lastPayloads.method} ${lastPayloads.url}
// Status: ${lastPayloads.statusCode} (${lastPayloads.durationMs}ms)

// Request Body:
${JSON.stringify(lastPayloads.request, null, 2)}

// Response Body:
${JSON.stringify(lastPayloads.response, null, 2)}`}
              </pre>
            </details>
          )}
        </section>

        {/* Right Column: Live Inventory & Low-Stock Alerts */}
        <section className="card">
          <h2>
            <span>Live Inventory</span>
            <button type="button" className="btn btn-secondary" onClick={refreshAll}>
              Refresh All
            </button>
          </h2>

          <table className="inventory-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Stock</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {products.map((item) => {
                const isLow = item.stock < 5 && item.stock > 0;
                const isOut = item.stock === 0;
                return (
                  <tr key={item.productId} className={isLow ? 'row-low-stock' : ''}>
                    <td><strong>{item.productId}</strong></td>
                    <td>{item.name}</td>
                    <td><strong>{item.stock}</strong></td>
                    <td>
                      {isOut ? (
                        <span className="stock-pill stock-out">Out of Stock</span>
                      ) : isLow ? (
                        <span className="stock-pill stock-low">⚠️ Low Stock (&lt; 5)</span>
                      ) : (
                        <span className="stock-pill stock-in">In Stock</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          {/* Domain Event Activity Feed (Notification Module) */}
          <h2 style={{ marginTop: '1.75rem' }}>
            <span>Activity Feed (Notification Module)</span>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>@EventListener</span>
          </h2>

          {notifications.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>No domain event logs yet.</p>
          ) : (
            <ul className="feed-list">
              {notifications.slice(0, 8).map((n) => {
                const isAlert = n.message.includes('REORDER NEEDED');
                const isRej = n.message.includes('rejected');
                const isConf = n.message.includes('confirmed');
                return (
                  <li
                    key={n.notificationId}
                    className={`feed-item ${
                      isAlert ? 'feed-alert' : isRej ? 'feed-rejected' : isConf ? 'feed-confirmed' : ''
                    }`}
                  >
                    <span>{n.message}</span>
                    <span className="feed-time">
                      {new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>

      {/* Bottom Section: Order History with Cancel Buttons */}
      <section className="card" style={{ marginTop: '1.5rem' }}>
        <h2>
          <span>Order History & Cancellation</span>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            Orders automatically restock on cancellation
          </span>
        </h2>

        {recentOrders.length === 0 ? (
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>No orders recorded yet.</p>
        ) : (
          <table className="inventory-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Date</th>
                <th>Status</th>
                <th>Line Items</th>
                <th>Details / Reason</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {recentOrders.map((ord) => (
                <tr key={ord.orderId}>
                  <td><strong>#{ord.orderId}</strong></td>
                  <td>{new Date(ord.createdAt).toLocaleTimeString()}</td>
                  <td>
                    <span className={`status-tag ${ord.status.toLowerCase()}`}>
                      {ord.status}
                    </span>
                  </td>
                  <td>
                    {ord.items && ord.items.length > 0 ? (
                      ord.items.map((i) => `${i.productId} (x${i.quantity})`).join(', ')
                    ) : (
                      'N/A'
                    )}
                  </td>
                  <td style={{ color: ord.reason ? '#fca5a5' : 'var(--text-muted)', fontSize: '0.8rem' }}>
                    {ord.reason || 'None'}
                  </td>
                  <td>
                    {ord.status === 'CONFIRMED' ? (
                      <button
                        type="button"
                        className="btn-cancel"
                        onClick={() => handleCancelOrder(ord.orderId)}
                        disabled={loading}
                      >
                        Cancel & Restock
                      </button>
                    ) : (
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
