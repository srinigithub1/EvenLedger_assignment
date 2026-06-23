package com.eventledger.account.dto;

import com.eventledger.account.domain.Transaction;
import com.eventledger.account.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionDto {
    private Long id;
    private String accountId;
    private TransactionType type;
    private BigDecimal amount;
    private String currency;
    private String eventId;
    private Instant createdAt;

    public static TransactionDto from(Transaction t) {
        TransactionDto dto = new TransactionDto();
        dto.id = t.getId();
        dto.accountId = t.getAccountId();
        dto.type = t.getType();
        dto.amount = t.getAmount();
        dto.currency = t.getCurrency();
        dto.eventId = t.getEventId();
        dto.createdAt = t.getCreatedAt();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
