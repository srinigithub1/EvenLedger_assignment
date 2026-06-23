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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies distributed-trace propagation: the Gateway generates a trace ID when
 * absent, forwards a client-supplied one unchanged, and echoes it on the response.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TracePropagationTest {

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

    private String body(String eventId) {
        return String.format(
                "{\"eventId\":\"%s\",\"accountId\":\"acct-trace\",\"type\":\"CREDIT\",\"amount\":50.00,"
                        + "\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:02:11Z\"}",
                eventId);
    }

    @Test
    void postEvent_traceIdPropagatedToAccountService() {
        wireMock.resetRequests();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body("trace-gen-" + System.nanoTime()), headers);

        restTemplate.postForEntity("/events", entity, Map.class);

        // No client trace ID sent -> Gateway must generate one (UUID-ish) and forward it.
        wireMock.verify(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))
                .withHeader("X-Trace-Id", matching("[a-f0-9-]+")));
    }

    @Test
    void postEvent_withClientTraceId_sameTraceIdForwarded() {
        wireMock.resetRequests();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Trace-Id", "my-trace-xyz");
        HttpEntity<String> entity = new HttpEntity<>(body("trace-fwd-" + System.nanoTime()), headers);

        restTemplate.postForEntity("/events", entity, Map.class);

        wireMock.verify(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))
                .withHeader("X-Trace-Id", equalTo("my-trace-xyz")));
    }

    @Test
    void response_containsTraceIdHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body("trace-resp-" + System.nanoTime()), headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/events", entity, Map.class);

        assertThat(resp.getHeaders().containsKey("X-Trace-Id")).isTrue();
    }
}
