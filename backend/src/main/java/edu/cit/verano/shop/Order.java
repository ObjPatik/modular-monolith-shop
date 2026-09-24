package edu.cit.verano.shop;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // CONFIRMED, REJECTED, CANCELLED

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    // Retained for database compatibility with Lab 1 schema
    @Column(name = "product_id", length = 50)
    private String productId = "P100";

    @Column(name = "quantity")
    private Integer quantity = 1;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(String status, String reason) {
        this.status = status;
        this.reason = reason;
        this.productId = "P100";
        this.quantity = 1;
        this.createdAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        if (item.getProductId() != null) {
            this.productId = item.getProductId();
        }
        if (item.getQuantity() > 0) {
            this.quantity = item.getQuantity();
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
}
