package com.ledgerline.repository;

import com.ledgerline.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<LedgerEntry> findByLedgerTransactionId(Long ledgerTransactionId);

    @Query("SELECT COALESCE(SUM(e.signedAmount), 0) FROM LedgerEntry e WHERE e.ledgerTransaction.id = :transactionId")
    BigDecimal sumSignedAmountByTransactionId(@Param("transactionId") Long transactionId);
}
