package edu.cit.verano.channel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * PACKAGE-PRIVATE entity tracking processed Tiangge event IDs.
 * Guarantees exactly-once processing across redelivered events and server restarts.
 */
@Entity
@Table(name = "tiangge_processed_events")
class TianggeProcessedEvent {

    @Id
    private String eventId;

    private String orderId;

    private String eventType;

    private Instant processedAt;

    public TianggeProcessedEvent() {}

    public TianggeProcessedEvent(String eventId, String orderId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}

