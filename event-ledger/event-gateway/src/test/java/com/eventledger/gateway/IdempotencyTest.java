package com.eventledger.gateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies idempotency: a repeated eventId is never re-applied downstream and
 * always returns the originally stored event.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class IdempotencyTest {

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

    private HttpEntity<String> eventEntity(String eventId, String accountId, String type, String amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"eventId\":\"%s\",\"accountId\":\"%s\",\"type\":\"%s\",\"amount\":%s,"
                        + "\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}",
                eventId, accountId, type, amount);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void sameEventId_submittedTwice_accountServiceCalledOnce() {
        String eventId = "idem-once-" + System.nanoTime();
        HttpEntity<String> entity = eventEntity(eventId, "acct-idem", "CREDIT", "100.00");

        ResponseEntity<Map> first = restTemplate.postForEntity("/events", entity, Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity("/events", entity, Map.class);

        wireMock.verify(1, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
        assertThat(first.getBody().get("eventId")).isEqualTo(eventId);
        assertThat(second.getBody().get("eventId")).isEqualTo(eventId);
    }

    @Test
    void differentEventIds_sameAccount_bothProcessed() {
        String suffix = "-" + System.nanoTime();
        restTemplate.postForEntity("/events",
                eventEntity("idem-1" + suffix, "acct-idem-multi", "CREDIT", "100.00"), Map.class);
        restTemplate.postForEntity("/events",
                eventEntity("idem-2" + suffix, "acct-idem-multi", "DEBIT", "40.00"), Map.class);

        wireMock.verify(2, postRequestedFor(urlPathMatching("/accounts/.*/transactions")));
    }

    @Test
    void duplicateReturnsOriginalBody() {
        String eventId = "idem-body-" + System.nanoTime();
        HttpEntity<String> entity = eventEntity(eventId, "acct-idem-body", "CREDIT", "123.45");

        ResponseEntity<Map> first = restTemplate.postForEntity("/events", entity, Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity("/events", entity, Map.class);

        assertThat(second.getBody().get("eventId")).isEqualTo(first.getBody().get("eventId"));
        assertThat(second.getBody().get("amount")).isEqualTo(first.getBody().get("amount"));
    }
}
