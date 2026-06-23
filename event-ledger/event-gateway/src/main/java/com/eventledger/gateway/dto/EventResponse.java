package com.eventledger.gateway.dto;

import com.eventledger.gateway.domain.Event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Outbound representation of a stored event. {@code type} is exposed as a String
 * for JSON clarity. {@code metadata} is wrapped from the entity's raw JSON string.
 */
public class EventResponse {

    private Long id;
    private String eventId;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private Instant receivedAt;
    private Map<String, Object> metadata;
    private String status;

    public static EventResponse from(Event e) {
        EventResponse r = new EventResponse();
        r.id = e.getId();
        r.eventId = e.getEventId();
        r.accountId = e.getAccountId();
        r.type = e.getType() != null ? e.getType().name() : null;
        r.amount = e.getAmount();
        r.currency = e.getCurrency();
        r.eventTimestamp = e.getEventTimestamp();
        r.receivedAt = e.getReceivedAt();
        // Simple approach: surface the stored JSON string under a "raw" key.
        r.metadata = e.getMetadata() != null ? Map.of("raw", e.getMetadata()) : null;
        r.status = e.getStatus();
        return r;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
