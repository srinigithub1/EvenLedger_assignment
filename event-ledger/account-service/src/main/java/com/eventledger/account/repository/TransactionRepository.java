package com.eventledger.account.repository;

import com.eventledger.account.domain.Transaction;
import com.eventledger.account.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findTop10ByAccountIdOrderByCreatedAtDesc(String accountId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.accountId = :accountId AND t.type = :type")
    BigDecimal sumByAccountIdAndType(@Param("accountId") String accountId, @Param("type") TransactionType type);
}
