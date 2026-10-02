package edu.cit.verano.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * PACKAGE-PRIVATE DTOs for Tiangge API communication.
 */
final class TianggeDtos {

    private TianggeDtos() {}

    record HeartbeatRequest(
            String appName,
            String startedAt,
            long uptimeSeconds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeartbeatResponse(
            Instant serverTime,
            Integer nextHeartbeatSeconds
    ) {}

    record ListingDto(
            String sellerSku,
            String title,
            String supplierSku
    ) {}

    record StockDto(
            String sellerSku,
            int available
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(
            List<FeedEvent> events,
            Long nextCursor
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(
            long seq,
            String eventId,
            String type,
            String orderId,
            Instant placedAt,
            Instant decisionDeadline,
            Instant cancelledAt,
            Instant confirmDeadline,
            List<FeedLine> lines,
            FeedBuyer buyer
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedLine(
            String sellerSku,
            int qty
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedBuyer(
            String name,
            String city
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DecisionRequest(
            String decision,
            String shopOrderId,
            String reason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DecisionResponse(
            String orderId,
            String status
    ) {}

    record ResolutionRequest(
            String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResolutionResponse(
            String orderId,
            String status
    ) {}

    record CancellationRequest(
            boolean restocked
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CancellationResponse(
            String orderId,
            String status
    ) {}
}

