package com.eventledger.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class BalanceResponse {
    private String accountId;
    private BigDecimal balance;
    private String currency;
    private Instant asOf;

    public BalanceResponse() {
    }

    public BalanceResponse(String accountId, BigDecimal balance, String currency, Instant asOf) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
        this.asOf = asOf;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public void setAsOf(Instant asOf) {
        this.asOf = asOf;
    }
}
