package com.eventledger.gateway;

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

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * One full happy-path flow: submit an event, confirm it is stored, and confirm
 * the Account Service received exactly one transaction carrying the right amount.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FullIntegrationTest {

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

    @Test
    void fullFlow_submitEvent_eventStoredAndAccountServiceCalled() {
        wireMock.stubFor(post(urlPathEqualTo("/accounts/acct-full/transactions"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":99}")));

        String eventId = "full-flow-" + System.nanoTime();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"eventId\":\"%s\",\"accountId\":\"acct-full\",\"type\":\"CREDIT\",\"amount\":250.00,"
                        + "\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}",
                eventId);

        ResponseEntity<Map> postResp =
                restTemplate.postForEntity("/events", new HttpEntity<>(body, headers), Map.class);
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> getResp = restTemplate.getForEntity("/events/" + eventId, Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("eventId")).isEqualTo(eventId);

        // Exactly one downstream transaction, carrying the submitted amount.
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/accounts/acct-full/transactions")));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/accounts/acct-full/transactions"))
                .withRequestBody(matchingJsonPath("$.amount", matching(".*250.*"))));
    }
}
