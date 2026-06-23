package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.Transaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.dto.AccountDetailsDto;
import com.eventledger.account.dto.TransactionDto;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Applies a CREDIT or DEBIT transaction to the given account. The account is
     * created on first use (the Gateway only calls this once per unique eventId,
     * so dedup is handled upstream).
     */
    @Transactional
    public Transaction applyTransaction(String accountId, TransactionType type, BigDecimal amount, String currency, String eventId) {
        Account account = accountRepository.findById(accountId)
                .orElseGet(() -> accountRepository.save(new Account(accountId)));

        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setEventId(eventId);
        tx.setCreatedAt(Instant.now());

        Transaction saved = transactionRepository.save(tx);

        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        return saved;
    }

    /**
     * Net balance = sum(CREDITs) - sum(DEBITs). Order-independent by construction.
     */
    public BigDecimal getBalance(String accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        BigDecimal credits = transactionRepository.sumByAccountIdAndType(accountId, TransactionType.CREDIT);
        BigDecimal debits = transactionRepository.sumByAccountIdAndType(accountId, TransactionType.DEBIT);
        return credits.subtract(debits);
    }

    public AccountDetailsDto getAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        List<TransactionDto> recent = transactionRepository
                .findTop10ByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(TransactionDto::from)
                .collect(Collectors.toList());
        AccountDetailsDto dto = new AccountDetailsDto();
        dto.setId(account.getId());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        dto.setRecentTransactions(recent);
        return dto;
    }
}
