package edu.cit.verano.channel;

import java.time.Instant;

/**
 * Public domain record representing the high-level operational state of the channel.
 */
public record ChannelStatus(
    String channelName,
    boolean isOnline,
    String instanceId,
    long lastFeedCursor,
    Instant lastHeartbeatAt
) {}

