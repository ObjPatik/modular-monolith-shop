package edu.cit.verano;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared application instance identifier generated at startup.
 * Required for X-Client-Instance header sent to both Tiangge and LegacySupply.
 */
@Component
public class AppInstance {

    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final String instanceId;
    private final Instant startedAt;

    public AppInstance() {
        this.instanceId = UUID.randomUUID().toString();
        this.startedAt = Instant.now();
        log.info("================================================================================");
        log.info("[AppInstance] Application started with INSTANCE ID: {}", this.instanceId);
        log.info("================================================================================");
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getUptimeSeconds() {
        return Duration.between(startedAt, Instant.now()).toSeconds();
    }
}

