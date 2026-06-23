package com.eventledger.account.controller;

import com.eventledger.account.domain.Transaction;
import com.eventledger.account.dto.AccountDetailsDto;
import com.eventledger.account.dto.ApplyTransactionRequest;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionDto;
import com.eventledger.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts/{accountId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDto applyTransaction(@PathVariable String accountId,
                                           @RequestBody @Valid ApplyTransactionRequest request) {
        Transaction tx = accountService.applyTransaction(
                accountId, request.getType(), request.getAmount(),
                request.getCurrency(), request.getEventId());
        return TransactionDto.from(tx);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        BigDecimal balance = accountService.getBalance(accountId);
        BalanceResponse resp = new BalanceResponse();
        resp.setAccountId(accountId);
        resp.setBalance(balance);
        resp.setCurrency("USD");
        resp.setAsOf(Instant.now());
        return resp;
    }

    @GetMapping("/accounts/{accountId}")
    public AccountDetailsDto getAccount(@PathVariable String accountId) {
        return accountService.getAccount(accountId);
    }
}
