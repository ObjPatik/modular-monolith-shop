package edu.cit.verano.channel;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * PACKAGE-PRIVATE entity maintaining the durable mapping between Tiangge orders
 * and local internal shop orders.
 */
@Entity
@Table(name = "tiangge_orders")
class TianggeOrderMapping {

    @Id
    @Column(name = "order_id", length = 100)
    private String orderId;

    @Column(name = "shop_order_id", length = 100)
    private String shopOrderId;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "lines_json", length = 3000)
    private String linesJson;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_city")
    private String buyerCity;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "decision_deadline")
    private Instant decisionDeadline;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    public TianggeOrderMapping() {}

    public TianggeOrderMapping(String orderId, String shopOrderId, String status, String linesJson,
                               String buyerName, String buyerCity, Instant placedAt, Instant decisionDeadline) {
        this.orderId = orderId;
        this.shopOrderId = shopOrderId;
        this.status = status;
        this.linesJson = linesJson;
        this.buyerName = buyerName;
        this.buyerCity = buyerCity;
        this.placedAt = placedAt;
        this.decisionDeadline = decisionDeadline;
        this.decidedAt = Instant.now();
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getShopOrderId() {
        return shopOrderId;
    }

    public void setShopOrderId(String shopOrderId) {
        this.shopOrderId = shopOrderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLinesJson() {
        return linesJson;
    }

    public void setLinesJson(String linesJson) {
        this.linesJson = linesJson;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerCity() {
        return buyerCity;
    }

    public void setBuyerCity(String buyerCity) {
        this.buyerCity = buyerCity;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(Instant placedAt) {
        this.placedAt = placedAt;
    }

    public Instant getDecisionDeadline() {
        return decisionDeadline;
    }

    public void setDecisionDeadline(Instant decisionDeadline) {
        this.decisionDeadline = decisionDeadline;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}

