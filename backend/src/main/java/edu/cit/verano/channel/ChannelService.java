package edu.cit.verano.channel;

/**
 * Public interface exposing marketplace channel operations.
 * Enforces modular monolith boundary: internal channel details, HTTP clients,
 * poller, translators, and entities remain strictly package-private.
 */
public interface ChannelService {

    /**
     * Manually triggers a heartbeat pulse to Tiangge marketplace.
     */
    void triggerHeartbeat();

    /**
     * Immediately pushes full inventory stock snapshot to Tiangge marketplace.
     */
    void syncStock();

    /**
     * Returns the operational status of the channel integration.
     */
    ChannelStatus getStatus();
}

