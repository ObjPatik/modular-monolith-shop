package edu.cit.verano.channel;

import edu.cit.verano.channel.TianggeDtos.FeedEvent;
import edu.cit.verano.channel.TianggeDtos.FeedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PACKAGE-PRIVATE background worker polling the Tiangge order feed.
 * Remembers the cursor durably in database so restarts carry on without resetting.
 * Guarantees exactly-once processing for each eventId.
 */
@Component
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    private final TianggeClient tianggeClient;
    private final TianggeOrderProcessor orderProcessor;
    private final TianggeFeedStateRepository feedStateRepository;
    private final TianggeProcessedEventRepository processedEventRepository;
    private final TianggeHeartbeatService heartbeatService;

    private final AtomicLong cursor = new AtomicLong(0L);
    private boolean initialized = false;

    TianggeFeedPoller(TianggeClient tianggeClient,
                      TianggeOrderProcessor orderProcessor,
                      TianggeFeedStateRepository feedStateRepository,
                      TianggeProcessedEventRepository processedEventRepository,
                      TianggeHeartbeatService heartbeatService) {
        this.tianggeClient = tianggeClient;
        this.orderProcessor = orderProcessor;
        this.feedStateRepository = feedStateRepository;
        this.processedEventRepository = processedEventRepository;
        this.heartbeatService = heartbeatService;
    }

    long getCursor() {
        return cursor.get();
    }

    private synchronized void initCursorIfNecessary() {
        if (!initialized) {
            feedStateRepository.findById(1L).ifPresentOrElse(state -> {
                cursor.set(state.getLastCursor());
                log.info("[TianggeFeedPoller] Resuming feed from durable database cursor: {}", cursor.get());
            }, () -> {
                cursor.set(0L);
                feedStateRepository.save(new TianggeFeedState(1L, 0L));
                log.info("[TianggeFeedPoller] Initialized durable feed cursor at: 0");
            });
            initialized = true;
        }
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void pollFeed() {
        if (!heartbeatService.isOnline()) {
            return;
        }

        initCursorIfNecessary();

        long currentCursor = cursor.get();
        try {
            FeedResponse feedResponse = tianggeClient.getFeed(currentCursor, 20);
            if (feedResponse == null) {
                return;
            }

            if (feedResponse.events() != null && !feedResponse.events().isEmpty()) {
                log.info("[TianggeFeedPoller] Received {} event(s) after cursor {}",
                        feedResponse.events().size(), currentCursor);

                for (FeedEvent event : feedResponse.events()) {
                    processEventSafely(event);
                    if (event.seq() > cursor.get()) {
                        cursor.set(event.seq());
                    }
                }
            }

            if (feedResponse.nextCursor() != null) {
                if (feedResponse.nextCursor() < cursor.get() && (feedResponse.events() == null || feedResponse.events().isEmpty())) {
                    log.warn("[TianggeFeedPoller] Server nextCursor ({}) is lower than local cursor ({}). Resetting cursor to match server reset.",
                            feedResponse.nextCursor(), cursor.get());
                    cursor.set(feedResponse.nextCursor());
                } else if (feedResponse.nextCursor() > cursor.get()) {
                    cursor.set(feedResponse.nextCursor());
                }
            }

            // Persist updated cursor durably
            if (cursor.get() != currentCursor) {
                feedStateRepository.save(new TianggeFeedState(1L, cursor.get()));
            }

        } catch (Exception ex) {
            log.warn("[TianggeFeedPoller] Error polling feed after cursor {}: {}", currentCursor, ex.getMessage());
        }
    }

    private void processEventSafely(FeedEvent event) {
        String eventId = event.eventId();

        // Exactly-once processing guarantee
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.info("[TianggeFeedPoller] Event {} already processed. Skipping duplicate delivery.", eventId);
            return;
        }

        if (event.orderId() != null && "ORDER_PLACED".equalsIgnoreCase(event.type()) && orderProcessor.isOrderAlreadyDecided(event.orderId())) {
            log.info("[TianggeFeedPoller] Order {} already decided. Skipping redelivery.", event.orderId());
            if (eventId != null) {
                processedEventRepository.save(new TianggeProcessedEvent(
                        eventId, event.orderId(), event.type(), Instant.now()
                ));
            }
            return;
        }

        try {
            if ("ORDER_PLACED".equalsIgnoreCase(event.type())) {
                orderProcessor.processOrderPlaced(event);
            } else if ("ORDER_CANCELLED".equalsIgnoreCase(event.type())) {
                orderProcessor.processOrderCancelled(event);
            } else {
                log.warn("[TianggeFeedPoller] Unknown feed event type: {}", event.type());
            }

            // Record event as successfully handled
            if (eventId != null) {
                processedEventRepository.save(new TianggeProcessedEvent(
                        eventId, event.orderId(), event.type(), Instant.now()
                ));
            }
        } catch (Exception ex) {
            log.error("[TianggeFeedPoller] Failed processing event {} for order {}: {}",
                    eventId, event.orderId(), ex.getMessage(), ex);
        }
    }
}

