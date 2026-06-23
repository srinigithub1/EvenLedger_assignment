package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.Transaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.dto.AccountDetailsDto;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link AccountService}. No Spring context is loaded; all
 * repository collaborators are mocked with Mockito so the tests exercise only
 * the service's business logic (account creation, balance math, lookups).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void applyTransaction_newAccount_createsAccountAndReturnsTransaction() {
        String accountId = "acct-new";
        // No account exists yet -> service must create one via the orElseGet branch.
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        // save(Account) is called both inside orElseGet (new account) and again to bump updatedAt.
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        // transactionRepository.save echoes back the persisted transaction.
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = accountService.applyTransaction(
                accountId, TransactionType.CREDIT, new BigDecimal("150.00"), "USD", "evt-001");

        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        assertEquals(TransactionType.CREDIT, result.getType());
        assertEquals(new BigDecimal("150.00"), result.getAmount());
        assertEquals("USD", result.getCurrency());
        assertEquals("evt-001", result.getEventId());

        // Verify a brand-new Account was persisted with the requested id.
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(2)).save(accountCaptor.capture());
        assertEquals(accountId, accountCaptor.getAllValues().get(0).getId());
    }

    @Test
    void applyTransaction_existingAccount_doesNotCreateNewAccount() {
        String accountId = "acct-existing";
        Account existing = new Account(accountId);
        // Account already exists -> orElseGet lambda never runs, so no new-Account save.
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = accountService.applyTransaction(
                accountId, TransactionType.DEBIT, new BigDecimal("40.00"), "USD", "evt-002");

        assertNotNull(result);
        assertEquals(accountId, result.getAccountId());
        // The orElseGet creation save is skipped, so save() is invoked exactly once
        // (the updatedAt bump) and only ever with the pre-existing instance.
        verify(accountRepository, times(1)).save(any(Account.class));
        verify(accountRepository, times(1)).save(eq(existing));
    }

    @Test
    void getBalance_mixedCreditsAndDebits_returnsCorrectNet() {
        String accountId = "acct-123";
        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(transactionRepository.sumByAccountIdAndType(accountId, TransactionType.CREDIT))
                .thenReturn(new BigDecimal("200"));
        when(transactionRepository.sumByAccountIdAndType(accountId, TransactionType.DEBIT))
                .thenReturn(new BigDecimal("75"));

        BigDecimal balance = accountService.getBalance(accountId);

        assertEquals(0, new BigDecimal("125").compareTo(balance));
        verify(accountRepository).existsById(accountId);
    }

    @Test
    void getBalance_accountNotFound_throwsAccountNotFoundException() {
        String accountId = "acct-missing";
        when(accountRepository.existsById(accountId)).thenReturn(false);

        assertThrows(AccountNotFoundException.class, () -> accountService.getBalance(accountId));

        // Should short-circuit before touching the transaction sums.
        verify(transactionRepository, never()).sumByAccountIdAndType(any(), any());
    }

    @Test
    void getAccount_existingAccount_returnsDetailsWithTransactions() {
        String accountId = "acct-with-tx";
        Account account = new Account(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Transaction t1 = transaction(accountId, TransactionType.CREDIT, "100.00", "evt-a");
        Transaction t2 = transaction(accountId, TransactionType.DEBIT, "30.00", "evt-b");
        when(transactionRepository.findTop10ByAccountIdOrderByCreatedAtDesc(accountId))
                .thenReturn(List.of(t1, t2));

        AccountDetailsDto dto = accountService.getAccount(accountId);

        assertNotNull(dto);
        assertEquals(accountId, dto.getId());
        assertEquals(2, dto.getRecentTransactions().size());
        assertEquals(TransactionType.CREDIT, dto.getRecentTransactions().get(0).getType());
        assertEquals(TransactionType.DEBIT, dto.getRecentTransactions().get(1).getType());
    }

    @Test
    void getAccount_accountNotFound_throwsAccountNotFoundException() {
        String accountId = "acct-missing";
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.getAccount(accountId));

        // No transaction lookup once the account is known to be absent.
        verify(transactionRepository, never()).findTop10ByAccountIdOrderByCreatedAtDesc(any());
    }

    @Test
    void getBalance_noTransactions_returnsZero() {
        String accountId = "acct-empty";
        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(transactionRepository.sumByAccountIdAndType(accountId, TransactionType.CREDIT))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByAccountIdAndType(accountId, TransactionType.DEBIT))
                .thenReturn(BigDecimal.ZERO);

        BigDecimal balance = accountService.getBalance(accountId);

        assertEquals(0, BigDecimal.ZERO.compareTo(balance));
    }

    private static Transaction transaction(String accountId, TransactionType type, String amount, String eventId) {
        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setType(type);
        tx.setAmount(new BigDecimal(amount));
        tx.setCurrency("USD");
        tx.setEventId(eventId);
        tx.setCreatedAt(Instant.now());
        return tx;
    }
}
