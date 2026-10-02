package edu.cit.verano.channel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * PACKAGE-PRIVATE entity storing the last read Tiangge order feed cursor.
 * Ensures the app resumes from where it stopped across restarts.
 */
@Entity
@Table(name = "tiangge_feed_state")
class TianggeFeedState {

    @Id
    private Long id = 1L;

    private Long lastCursor = 0L;

    private Instant updatedAt;

    public TianggeFeedState() {
        this.updatedAt = Instant.now();
    }

    public TianggeFeedState(Long id, Long lastCursor) {
        this.id = id;
        this.lastCursor = lastCursor;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLastCursor() {
        return lastCursor != null ? lastCursor : 0L;
    }

    public void setLastCursor(Long lastCursor) {
        this.lastCursor = lastCursor;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

