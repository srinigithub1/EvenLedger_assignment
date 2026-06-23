package com.eventledger.gateway;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates graceful degradation: POST fails fast with 503 when Account Service
 * is down, while GET endpoints keep serving from the Gateway's own DB.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ResiliencyTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideAccountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", wireMock::baseUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    private void stubOk() {
        wireMock.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));
    }

    private void stubFault() {
        wireMock.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    private HttpEntity<String> eventEntity(String eventId, String accountId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"eventId\":\"%s\",\"accountId\":\"%s\",\"type\":\"CREDIT\",\"amount\":50.00,"
                        + "\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}",
                eventId, accountId);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void postEvent_accountServiceDown_returns503() {
        wireMock.resetRequests();
        stubFault();
        ResponseEntity<Map> resp = restTemplate.postForEntity("/events",
                eventEntity("res-down-" + System.nanoTime(), "acct-res"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void postEvent_accountServiceDown_respondsWithinTimeout() {
        wireMock.resetRequests();
        stubFault();
        long start = System.currentTimeMillis();
        restTemplate.postForEntity("/events",
                eventEntity("res-timeout-" + System.nanoTime(), "acct-res"), Map.class);
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isLessThan(15_000L);
    }

    @Test
    void getEvent_accountServiceDown_returns200() {
        // Persist an event while the downstream is healthy.
        stubOk();
        String eventId = "res-get-" + System.nanoTime();
        restTemplate.postForEntity("/events", eventEntity(eventId, "acct-res-get"), Map.class);

        // Now the downstream is down; the GET must still succeed from local DB.
        stubFault();
        ResponseEntity<Map> resp = restTemplate.getForEntity("/events/" + eventId, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("eventId")).isEqualTo(eventId);
    }

    @Test
    void getEventsByAccount_accountServiceDown_returns200() {
        stubOk();
        String accountId = "acct-res-list-" + System.nanoTime();
        String eventId = "res-list-" + System.nanoTime();
        restTemplate.postForEntity("/events", eventEntity(eventId, accountId), Map.class);

        stubFault();
        ResponseEntity<List> resp =
                restTemplate.getForEntity("/events?account=" + accountId, List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }
}
