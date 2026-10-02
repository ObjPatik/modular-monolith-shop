package edu.cit.verano.channel;

import edu.cit.verano.AppInstance;
import edu.cit.verano.channel.TianggeDtos.HeartbeatRequest;
import edu.cit.verano.channel.TianggeDtos.HeartbeatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PACKAGE-PRIVATE service managing instance heartbeats to Tiangge marketplace.
 * Sends first heartbeat immediately at startup before any other call,
 * followed by heartbeats every 30 seconds to maintain 'App online' status.
 */
@Component
class TianggeHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(TianggeHeartbeatService.class);

    private final TianggeClient tianggeClient;
    private final AppInstance appInstance;
    private final TianggeListingPublisher listingPublisher;
    private final TianggeStockSync stockSync;

    private final AtomicBoolean isOnline = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastHeartbeatAt = new AtomicReference<>(null);

    TianggeHeartbeatService(TianggeClient tianggeClient,
                           AppInstance appInstance,
                           TianggeListingPublisher listingPublisher,
                           TianggeStockSync stockSync) {
        this.tianggeClient = tianggeClient;
        this.appInstance = appInstance;
        this.listingPublisher = listingPublisher;
        this.stockSync = stockSync;
    }

    boolean isOnline() {
        return isOnline.get();
    }

    Instant getLastHeartbeatAt() {
        return lastHeartbeatAt.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[TianggeHeartbeatService] Application ready. Initiating startup sequence...");
        try {
            // 1. First heartbeat BEFORE any other call
            sendHeartbeat();

            // 2. Publish listings
            listingPublisher.publishListings();

            // 3. Publish initial stock
            stockSync.publishStock();

            log.info("[TianggeHeartbeatService] Go-live initialization complete. Shop is now live!");
        } catch (Exception ex) {
            log.error("[TianggeHeartbeatService] Startup go-live sequence failed: {}", ex.getMessage(), ex);
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void scheduledHeartbeat() {
        sendHeartbeat();
    }

    void sendHeartbeat() {
        try {
            String isoStartedAt = DateTimeFormatter.ISO_INSTANT.format(appInstance.getStartedAt());
            HeartbeatRequest request = new HeartbeatRequest(
                    "shop-monolith",
                    isoStartedAt,
                    appInstance.getUptimeSeconds()
            );

            log.info("[TianggeHeartbeatService] Sending heartbeat (Uptime: {}s, Instance: {})...",
                    request.uptimeSeconds(), appInstance.getInstanceId());

            HeartbeatResponse response = tianggeClient.sendHeartbeat(request);
            if (response != null) {
                isOnline.set(true);
                lastHeartbeatAt.set(Instant.now());
                log.info("[TianggeHeartbeatService] Heartbeat ACK received from Tiangge. ServerTime: {}", response.serverTime());
            }
        } catch (Exception ex) {
            isOnline.set(false);
            log.warn("[TianggeHeartbeatService] Heartbeat failed: {}", ex.getMessage());
        }
    }
}

