package com.eventledger.account.dto;

import java.time.Instant;
import java.util.List;

public class AccountDetailsDto {
    private String id;
    private Instant createdAt;
    private Instant updatedAt;
    private List<TransactionDto> recentTransactions;

    public AccountDetailsDto() {
    }

    public AccountDetailsDto(String id, Instant createdAt, Instant updatedAt, List<TransactionDto> recentTransactions) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.recentTransactions = recentTransactions;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TransactionDto> getRecentTransactions() {
        return recentTransactions;
    }

    public void setRecentTransactions(List<TransactionDto> recentTransactions) {
        this.recentTransactions = recentTransactions;
    }
}
