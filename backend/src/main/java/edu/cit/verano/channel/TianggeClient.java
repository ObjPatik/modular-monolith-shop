package edu.cit.verano.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.cit.verano.AppInstance;
import edu.cit.verano.channel.TianggeDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * PACKAGE-PRIVATE HTTP client communicating with Tiangge Marketplace API.
 * Encapsulates authentication, timeouts, retries, and instance identification.
 */
@Component
class TianggeClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeClient.class);
    private static final int TIMEOUT_MS = 4000;
    private static final int MAX_RETRIES = 3;

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final AppInstance appInstance;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    TianggeClient(
            @Value("${tiangge.base-url:${TIANGGE_BASE_URL:https://legacysupply.onrender.com/tiangge/v1}}") String baseUrl,
            @Value("${tiangge.client-id:${TIANGGE_CLIENT_ID:${LS_CLIENT_ID:22-6077-335}}}") String clientId,
            @Value("${tiangge.api-key:${TIANGGE_API_KEY:${LS_API_KEY:LSK-024F44D8F440F68F4B91}}}") String apiKey,
            AppInstance appInstance
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.appInstance = appInstance;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(TIMEOUT_MS));

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(this.objectMapper);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().add(0, jsonConverter);
    }

    String getClientId() {
        return clientId;
    }

    String getInstanceId() {
        return appInstance.getInstanceId();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Client-Id", clientId);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("X-Client-Instance", appInstance.getInstanceId());
        return headers;
    }

    /**
     * Sends heartbeat pulse to Tiangge marketplace.
     */
    HeartbeatResponse sendHeartbeat(HeartbeatRequest request) {
        String url = baseUrl + "/instances/heartbeat";
        return executeWithRetry(() -> {
            HttpEntity<HeartbeatRequest> entity = new HttpEntity<>(request, createHeaders());
            ResponseEntity<HeartbeatResponse> response = restTemplate.postForEntity(url, entity, HeartbeatResponse.class);
            return response.getBody();
        }, "sendHeartbeat");
    }

    /**
     * Publishes 1-10 product listings with LegacySupply supplier mapping.
     */
    void publishListings(List<ListingDto> listings) {
        String url = baseUrl + "/listings";
        executeWithRetry(() -> {
            HttpEntity<List<ListingDto>> entity = new HttpEntity<>(listings, createHeaders());
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
            log.info("[TianggeClient] Successfully published {} listings", listings.size());
            return null;
        }, "publishListings");
    }

    /**
     * Publishes snapshot of available inventory stock numbers.
     */
    void publishStock(List<StockDto> stock) {
        String url = baseUrl + "/stock";
        executeWithRetry(() -> {
            HttpEntity<List<StockDto>> entity = new HttpEntity<>(stock, createHeaders());
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
            log.info("[TianggeClient] Stock published successfully: {}", stock);
            return null;
        }, "publishStock");
    }

    /**
     * Reads event feed starting strictly after the provided sequence cursor.
     */
    FeedResponse getFeed(long afterCursor, int limit) {
        String url = String.format("%s/feed?after=%d&limit=%d", baseUrl, afterCursor, Math.max(1, Math.min(50, limit)));
        return executeWithRetry(() -> {
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<FeedResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, FeedResponse.class);
            return response.getBody();
        }, "getFeed");
    }

    /**
     * Reports ACCEPTED, REJECTED, or BACKORDERED decision for an order.
     */
    DecisionResponse sendDecision(String orderId, DecisionRequest request) {
        String url = baseUrl + "/orders/" + orderId + "/decision";
        return executeWithRetry(() -> {
            try {
                HttpEntity<DecisionRequest> entity = new HttpEntity<>(request, createHeaders());
                ResponseEntity<DecisionResponse> response = restTemplate.postForEntity(url, entity, DecisionResponse.class);
                log.info("[TianggeClient] Decision '{}' sent for order {} -> shopOrderId: {}",
                        request.decision(), orderId, request.shopOrderId());
                return response.getBody();
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    log.warn("[TianggeClient] 409 Conflict sending decision for order {}: {}. Safe idempotent response.",
                            orderId, ex.getResponseBodyAsString());
                    return new DecisionResponse(orderId, request.decision());
                }
                throw ex;
            }
        }, "sendDecision");
    }

    /**
     * Resolves a BACKORDERED order to ACCEPTED or CANCELLED when delivery arrives.
     */
    ResolutionResponse sendResolution(String orderId, ResolutionRequest request) {
        String url = baseUrl + "/orders/" + orderId + "/resolution";
        return executeWithRetry(() -> {
            try {
                HttpEntity<ResolutionRequest> entity = new HttpEntity<>(request, createHeaders());
                ResponseEntity<ResolutionResponse> response = restTemplate.postForEntity(url, entity, ResolutionResponse.class);
                log.info("[TianggeClient] Resolution '{}' sent for order {}", request.status(), orderId);
                return response.getBody();
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    log.warn("[TianggeClient] 409 Conflict sending resolution for order {}: {}. Already resolved.",
                            orderId, ex.getResponseBodyAsString());
                    return new ResolutionResponse(orderId, request.status());
                }
                throw ex;
            }
        }, "sendResolution");
    }

    /**
     * Confirms buyer cancellation and reports inventory restock within 60s.
     */
    CancellationResponse sendCancellation(String orderId, CancellationRequest request) {
        String url = baseUrl + "/orders/" + orderId + "/cancellation";
        return executeWithRetry(() -> {
            try {
                HttpEntity<CancellationRequest> entity = new HttpEntity<>(request, createHeaders());
                ResponseEntity<CancellationResponse> response = restTemplate.postForEntity(url, entity, CancellationResponse.class);
                log.info("[TianggeClient] Cancellation confirmed for order {}", orderId);
                return response.getBody();
            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    log.warn("[TianggeClient] 409 Conflict confirming cancellation for order {}: {}. Already confirmed.",
                            orderId, ex.getResponseBodyAsString());
                    return new CancellationResponse(orderId, "CONFIRMED");
                }
                throw ex;
            }
        }, "sendCancellation");
    }

    /**
     * Resilience helper: Retries operations on 503 Service Unavailable or network timeouts.
     */
    private <T> T executeWithRetry(TianggeOperation<T> operation, String opName) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return operation.execute();
            } catch (HttpStatusCodeException httpEx) {
                lastException = httpEx;
                if (httpEx.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE ||
                    httpEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("[TianggeClient] Server error {} on attempt {}/{} for {}: {}",
                            httpEx.getStatusCode(), attempt, MAX_RETRIES, opName, httpEx.getResponseBodyAsString());
                } else {
                    // Do not retry 4xx errors
                    throw httpEx;
                }
            } catch (ResourceAccessException timeoutEx) {
                lastException = timeoutEx;
                log.warn("[TianggeClient] Timeout/connection error on attempt {}/{} for {}: {}",
                        attempt, MAX_RETRIES, opName, timeoutEx.getMessage());
            } catch (Exception generalEx) {
                lastException = generalEx;
                log.warn("[TianggeClient] Unexpected error on attempt {}/{} for {}: {}",
                        attempt, MAX_RETRIES, opName, generalEx.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                backoff(attempt);
            }
        }

        if (lastException instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException("Tiangge operation '" + opName + "' failed after " + MAX_RETRIES + " attempts", lastException);
    }

    private void backoff(int attempt) {
        try {
            long delay = (long) Math.pow(2, attempt - 1) * 300L; // 300ms, 600ms, 1200ms
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface TianggeOperation<T> {
        T execute() throws Exception;
    }
}
