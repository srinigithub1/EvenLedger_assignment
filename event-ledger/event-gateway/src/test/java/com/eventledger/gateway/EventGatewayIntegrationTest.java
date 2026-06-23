package com.eventledger.gateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the Event Gateway REST API. The downstream Account Service
 * is faked with WireMock; the Gateway is pointed at it via a dynamic property.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EventGatewayIntegrationTest {

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

    @BeforeEach
    void stubAccountServiceOk() {
        wireMock.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1}")));
    }

    private HttpEntity<String> eventEntity(String eventId, String accountId, String type,
            String amount, String eventTimestamp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"eventId\":\"%s\",\"accountId\":\"%s\",\"type\":\"%s\",\"amount\":%s,"
                        + "\"currency\":\"USD\",\"eventTimestamp\":\"%s\"}",
                eventId, accountId, type, amount, eventTimestamp);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void postEvent_newEvent_returns201AndCallsAccountService() {
        String eventId = "intg-new-" + System.nanoTime();
        ResponseEntity<Map> resp = restTemplate.postForEntity("/events",
                eventEntity(eventId, "acct-intg", "CREDIT", "150.00", "2026-05-15T14:02:11Z"),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("eventId")).isEqualTo(eventId);

        wireMock.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
    }

    @Test
    void postEvent_duplicateEventId_returns200AndDoesNotCallAccountService() {
        String eventId = "intg-dup-" + System.nanoTime();
        HttpEntity<String> entity =
                eventEntity(eventId, "acct-intg", "CREDIT", "150.00", "2026-05-15T14:02:11Z");

        ResponseEntity<Map> first = restTemplate.postForEntity("/events", entity, Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity("/events", entity, Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Duplicate must never be forwarded downstream: exactly one call total.
        wireMock.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
    }

    @Test
    void getEvent_existingEvent_returns200() {
        String eventId = "intg-get-" + System.nanoTime();
        restTemplate.postForEntity("/events",
                eventEntity(eventId, "acct-intg", "CREDIT", "150.00", "2026-05-15T14:02:11Z"),
                Map.class);

        ResponseEntity<Map> resp = restTemplate.getForEntity("/events/" + eventId, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("eventId")).isEqualTo(eventId);
    }

    @Test
    void getEvent_unknownId_returns404() {
        ResponseEntity<Map> resp =
                restTemplate.getForEntity("/events/nonexistent-id-" + System.nanoTime(), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getEventsByAccount_returnsEventsOrderedByEventTimestamp() {
        String accountId = "acct-order-" + System.nanoTime();
        // Submit in REVERSE timestamp order (c, b, a) to prove server-side sorting.
        restTemplate.postForEntity("/events",
                eventEntity("evt-c-" + accountId, accountId, "CREDIT", "10.00", "2026-01-01T12:00:00Z"),
                Map.class);
        restTemplate.postForEntity("/events",
                eventEntity("evt-b-" + accountId, accountId, "CREDIT", "10.00", "2026-01-01T11:00:00Z"),
                Map.class);
        restTemplate.postForEntity("/events",
                eventEntity("evt-a-" + accountId, accountId, "CREDIT", "10.00", "2026-01-01T10:00:00Z"),
                Map.class);

        ResponseEntity<List> resp =
                restTemplate.getForEntity("/events?account=" + accountId, List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> events = resp.getBody();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).get("eventId")).isEqualTo("evt-a-" + accountId);
        assertThat(events.get(2).get("eventId")).isEqualTo("evt-c-" + accountId);
    }

    @Test
    void healthEndpoint_returns200() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/health", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("status")).isEqualTo("UP");
    }
}
