package edu.cit.verano.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PACKAGE-PRIVATE HTTP client communicating with LegacySupply Distribution API.
 * Encapsulates XML serialization, session authentication, 3s timeout, and backoff retries.
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_RETRIES = 3;

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;

    private final RestTemplate restTemplate;
    private final XmlMapper xmlMapper;
    private final AtomicReference<String> sessionTokenRef = new AtomicReference<>(null);

    LegacySupplyClient(
            @Value("${legacysupply.base-url:${LS_BASE_URL:https://legacysupply.onrender.com/api/v1}}") String baseUrl,
            @Value("${legacysupply.client-id:${LS_CLIENT_ID:22-6077-335}}") String clientId,
            @Value("${legacysupply.api-key:${LS_API_KEY:LSK-024F44D8F440F68F4B91}}") String apiKey
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(TIMEOUT_MS));

        this.xmlMapper = new XmlMapper();
        MappingJackson2XmlHttpMessageConverter xmlConverter = new MappingJackson2XmlHttpMessageConverter(this.xmlMapper);
        xmlConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_XML, MediaType.TEXT_XML));

        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().add(0, xmlConverter);
    }

    /**
     * Authenticates with LegacySupply and returns a fresh session token.
     */
    synchronized String authenticate() {
        String authUrl = baseUrl + "/auth/token";
        AuthRequestXml request = new AuthRequestXml(clientId, apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML));
        HttpEntity<AuthRequestXml> entity = new HttpEntity<>(request, headers);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("[LegacySupplyClient] Authenticating attempt {}/{} for client {}", attempt, MAX_RETRIES, clientId);
                ResponseEntity<AuthResponseXml> response = restTemplate.postForEntity(authUrl, entity, AuthResponseXml.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    String token = response.getBody().getSessionToken();
                    sessionTokenRef.set(token);
                    log.info("[LegacySupplyClient] Authentication successful. Token: {}...", token.substring(0, Math.min(8, token.length())));
                    return token;
                }
            } catch (Exception ex) {
                lastException = ex;
                log.warn("[LegacySupplyClient] Auth attempt {} failed: {}", attempt, ex.getMessage());
                if (attempt < MAX_RETRIES) {
                    backoff(attempt);
                }
            }
        }
        throw new IllegalStateException("Failed to authenticate with LegacySupply after " + MAX_RETRIES + " attempts", lastException);
    }

    private String getValidSessionToken() {
        String token = sessionTokenRef.get();
        if (token == null || token.isBlank()) {
            token = authenticate();
        }
        return token;
    }

    /**
     * Submits a purchase order with idempotency (X-Request-Id) and session recovery.
     */
    public PurchaseOrderAckXml submitPurchaseOrder(String sku, int qty, String buyerRef, String requestId) {
        String url = baseUrl + "/purchase-orders";
        PurchaseOrderXml orderBody = new PurchaseOrderXml(sku, qty, buyerRef);

        return executeWithRetryAndSession(token -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setAccept(List.of(MediaType.APPLICATION_XML));
            headers.set("X-LS-Session", token);
            if (requestId != null && !requestId.isBlank()) {
                headers.set("X-Request-Id", requestId);
            }

            HttpEntity<PurchaseOrderXml> entity = new HttpEntity<>(orderBody, headers);
            ResponseEntity<PurchaseOrderAckXml> response = restTemplate.postForEntity(url, entity, PurchaseOrderAckXml.class);
            return response.getBody();
        });
    }

    /**
     * Retrieves status of a specific purchase order by PO number.
     */
    public PurchaseOrderStatusXml getOrderStatus(String poNumber) {
        String url = baseUrl + "/purchase-orders/" + poNumber;

        return executeWithRetryAndSession(token -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_XML));
            headers.set("X-LS-Session", token);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<PurchaseOrderStatusXml> response = restTemplate.exchange(url, HttpMethod.GET, entity, PurchaseOrderStatusXml.class);
            return response.getBody();
        });
    }

    /**
     * Queries orders matching a specific BuyerRef.
     */
    public Optional<PurchaseOrderAckXml> getOrderByBuyerRef(String buyerRef) {
        String url = baseUrl + "/purchase-orders?buyerRef=" + buyerRef;

        try {
            PurchaseOrderListXml list = executeWithRetryAndSession(token -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_XML));
                headers.set("X-LS-Session", token);

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<PurchaseOrderListXml> response = restTemplate.exchange(url, HttpMethod.GET, entity, PurchaseOrderListXml.class);
                return response.getBody();
            });

            if (list != null && list.getOrders() != null && !list.getOrders().isEmpty()) {
                return Optional.of(list.getOrders().get(0));
            }
        } catch (Exception ex) {
            log.warn("[LegacySupplyClient] Failed to query buyerRef {}: {}", buyerRef, ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Executes an operation with exponential backoff retry and automatic session token renewal.
     */
    private <T> T executeWithRetryAndSession(SupplierOperation<T> operation) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String token = getValidSessionToken();
            try {
                return operation.execute(token);
            } catch (HttpStatusCodeException httpEx) {
                lastException = httpEx;
                // If 401 Unauthorized, token likely expired (E-AUTH-07, etc.)
                if (httpEx.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    log.info("[LegacySupplyClient] 401 Unauthorized received. Re-authenticating session...");
                    sessionTokenRef.set(null); // invalidate token
                    try {
                        String freshToken = authenticate();
                        // Retry immediately with fresh token
                        return operation.execute(freshToken);
                    } catch (Exception reauthEx) {
                        lastException = reauthEx;
                        log.warn("[LegacySupplyClient] Re-auth failed: {}", reauthEx.getMessage());
                    }
                } else if (httpEx.getStatusCode() == HttpStatus.CONFLICT) {
                    // 409 Conflict: idempotency response or already processed
                    log.warn("[LegacySupplyClient] 409 Conflict: {}", httpEx.getResponseBodyAsString());
                    throw httpEx;
                } else if (httpEx.getStatusCode().is5xxServerError() || httpEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("[LegacySupplyClient] Server error {} on attempt {}/{}: {}",
                            httpEx.getStatusCode(), attempt, MAX_RETRIES, httpEx.getResponseBodyAsString());
                } else {
                    // 4xx client errors (400, 422) shouldn't be retried
                    throw httpEx;
                }
            } catch (ResourceAccessException timeoutEx) {
                // Timeout or network connection error
                lastException = timeoutEx;
                log.warn("[LegacySupplyClient] Timeout/connection error on attempt {}/{}: {}",
                        attempt, MAX_RETRIES, timeoutEx.getMessage());
            } catch (Exception generalEx) {
                lastException = generalEx;
                log.warn("[LegacySupplyClient] Error on attempt {}/{}: {}", attempt, MAX_RETRIES, generalEx.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                backoff(attempt);
            }
        }

        if (lastException instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException("LegacySupply call failed after " + MAX_RETRIES + " attempts", lastException);
    }

    private void backoff(int attempt) {
        try {
            long delay = (long) Math.pow(2, attempt - 1) * 500L; // 500ms, 1000ms, 2000ms
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface SupplierOperation<T> {
        T execute(String sessionToken) throws Exception;
    }
}
