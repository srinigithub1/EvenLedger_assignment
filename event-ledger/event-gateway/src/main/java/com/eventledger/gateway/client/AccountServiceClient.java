package com.eventledger.gateway.client;

import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for the internal Account Service. Calls are wrapped in a
 * Resilience4j circuit breaker so that repeated downstream failures short-circuit
 * to the fallback instead of piling up slow/failing requests.
 */
@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);

    @Value("${account-service.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;

    public AccountServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    public AccountTransactionResponse applyTransaction(String accountId, AccountTransactionRequest request) {
        String url = baseUrl + "/accounts/" + accountId + "/transactions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Propagate the current request's trace ID to the downstream Account Service
        // so both services log under the same traceId.
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            headers.set("X-Trace-Id", traceId);
        }
        HttpEntity<AccountTransactionRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<AccountTransactionResponse> response =
                restTemplate.postForEntity(url, entity, AccountTransactionResponse.class);
        return response.getBody();
    }

    /**
     * Fallback invoked by the circuit breaker on any failure (timeout, connection
     * refused, downstream error, or open circuit). Translates the cause into a
     * domain exception that the API layer maps to HTTP 503.
     */
    private AccountTransactionResponse applyTransactionFallback(String accountId,
            AccountTransactionRequest request, Throwable ex) {
        log.warn("Circuit breaker fallback triggered for accountId={}: {}", accountId, ex.getMessage());
        throw new AccountServiceUnavailableException(
                "Account Service unavailable for accountId=" + accountId, ex);
    }
}
