package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.EventType;

import java.math.BigDecimal;

/**
 * Request body sent to Account Service when applying a transaction.
 * {@code eventId} is forwarded so Account Service can deduplicate on its side.
 */
public class AccountTransactionRequest {

    private String eventId;
    private EventType type;
    private BigDecimal amount;
    private String currency;

    public AccountTransactionRequest() {
    }

    public AccountTransactionRequest(String eventId, EventType type, BigDecimal amount, String currency) {
        this.eventId = eventId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
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
}
