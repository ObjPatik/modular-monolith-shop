import React, { useState, useEffect } from 'react';

const API_BASE_URL = 'http://localhost:8080/api';

export default function App() {
  const [products, setProducts] = useState([
    { productId: 'P100', name: 'Wireless Mouse', stock: 25 },
    { productId: 'P200', name: 'Mechanical Keyboard', stock: 10 },
    { productId: 'P300', name: 'USB-C Hub', stock: 0 }
  ]);
  const [selectedProductId, setSelectedProductId] = useState('P100');
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(false);
  const [orderResult, setOrderResult] = useState(null);
  const [recentOrders, setRecentOrders] = useState([]);
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
        setApiError(null);
      }
    } catch (err) {
      console.warn('Could not connect to backend inventory endpoint:', err.message);
      // Keep initial baseline seed products if backend is not yet started
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
      console.warn('Could not connect to backend orders endpoint:', err.message);
    }
  };

  useEffect(() => {
    fetchInventory();
    fetchOrders();
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selectedProductId) return;

    setLoading(true);
    setApiError(null);

    const requestPayload = {
      productId: selectedProductId,
      quantity: parseInt(quantity, 10)
    };

    try {
      const startTime = performance.now();
      const res = await fetch(`${API_BASE_URL}/orders`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestPayload)
      });

      const responsePayload = await res.json();
      const durationMs = Math.round(performance.now() - startTime);

      setOrderResult(responsePayload);
      setLastPayloads({
        url: `${API_BASE_URL}/orders`,
        method: 'POST',
        request: requestPayload,
        response: responsePayload,
        statusCode: res.status,
        durationMs
      });

      // Refresh products and orders
      await fetchInventory();
      await fetchOrders();
    } catch (err) {
      setApiError(`Failed to submit order: ${err.message}. Is the Spring Boot backend running at ${API_BASE_URL}?`);
    } finally {
      setLoading(false);
    }
  };

  const selectedProduct = products.find((p) => p.productId === selectedProductId);

  return (
    <div className="container">
      <header>
        <h1>Modular Monolith Store</h1>
        <p>Order & Inventory In-Process Integration with Supabase PostgreSQL</p>
        <span className="badge badge-arch">Architecture: In-Process Monolith Boundary</span>
      </header>

      {apiError && (
        <div className="error-banner">
          ⚠️ {apiError}
        </div>
      )}

      <div className="grid-layout">
        {/* Order Placement Form */}
        <section className="card">
          <h2>Place an Order</h2>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="product-select">Select Product</label>
              <select
                id="product-select"
                className="form-control"
                value={selectedProductId}
                onChange={(e) => setSelectedProductId(e.target.value)}
                disabled={loading}
              >
                {products.map((item) => (
                  <option key={item.productId} value={item.productId}>
                    {item.productId} — {item.name} ({item.stock} in stock)
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
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
                required
              />
            </div>

            <button
              type="submit"
              className="btn"
              disabled={loading || !selectedProductId}
            >
              {loading ? 'Processing Order...' : 'Submit Order'}
            </button>
          </form>

          {/* Result Area */}
          {orderResult && (
            <div className={`result-box ${orderResult.status.toLowerCase()}`}>
              <div className="result-header">
                <span className="result-title">Order Result</span>
                <span className={`status-tag ${orderResult.status.toLowerCase()}`}>
                  {orderResult.status}
                </span>
              </div>

              {orderResult.status === 'CONFIRMED' ? (
                <div>
                  <div className="result-detail">
                    <span>Status:</span> <strong>Order Confirmed!</strong>
                  </div>
                  {orderResult.inventory && (
                    <div className="result-detail">
                      <span>Remaining Stock for {orderResult.inventory.name} ({orderResult.inventory.productId}):</span>{' '}
                      <strong>{orderResult.inventory.stock} units</strong>
                    </div>
                  )}
                </div>
              ) : (
                <div>
                  <div className="result-detail">
                    <span>Status:</span> <strong>Order Rejected</strong>
                  </div>
                  <div className="result-detail">
                    <span>Reason:</span> {orderResult.reason || 'Insufficient stock or invalid product.'}
                  </div>
                  {orderResult.inventory && (
                    <div className="result-detail">
                      <span>Current Available Stock:</span> {orderResult.inventory.stock} units
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Network Tab Evidence Helper */}
          {lastPayloads && (
            <details className="evidence-box">
              <summary>📋 View Network Tab Evidence (Inspect Payloads)</summary>
              <pre>
{`// HTTP ${lastPayloads.method} ${lastPayloads.url}
// Status: ${lastPayloads.statusCode} OK (${lastPayloads.durationMs}ms)

// Request Body:
${JSON.stringify(lastPayloads.request, null, 2)}

// Response Body:
${JSON.stringify(lastPayloads.response, null, 2)}`}
              </pre>
            </details>
          )}
        </section>

        {/* Live Inventory & Orders Status */}
        <section className="card">
          <h2>
            <span>Live Inventory</span>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => { fetchInventory(); fetchOrders(); }}
            >
              Refresh
            </button>
          </h2>

          <table className="inventory-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>Name</th>
                <th>Stock</th>
              </tr>
            </thead>
            <tbody>
              {products.map((item) => (
                <tr key={item.productId}>
                  <td><strong>{item.productId}</strong></td>
                  <td>{item.name}</td>
                  <td>
                    <span
                      className={`stock-pill ${
                        item.stock > 10
                          ? 'stock-in'
                          : item.stock > 0
                          ? 'stock-low'
                          : 'stock-out'
                      }`}
                    >
                      {item.stock} left
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <h2 style={{ marginTop: '2rem' }}>Recent Orders</h2>
          {recentOrders.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              No orders placed yet. Submit an order above to record one.
            </p>
          ) : (
            <table className="inventory-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Product</th>
                  <th>Qty</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {recentOrders.slice(0, 5).map((ord) => (
                  <tr key={ord.orderId}>
                    <td>#{ord.orderId}</td>
                    <td>{ord.productId}</td>
                    <td>{ord.quantity}</td>
                    <td>
                      <span className={`status-tag ${ord.status.toLowerCase()}`}>
                        {ord.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </div>
  );
}
