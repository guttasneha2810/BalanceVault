package com.ledgerline.repository;

import com.ledgerline.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByTransactionReference(String transactionReference);

    List<LedgerTransaction> findAllByOrderByCreatedAtDesc();

    List<LedgerTransaction> findByInitiatedByUserIdOrderByCreatedAtDesc(Long initiatedByUserId);

    @Query("SELECT DISTINCT t FROM LedgerTransaction t " +
           "JOIN LedgerEntry e ON e.ledgerTransaction = t " +
           "JOIN e.account a " +
           "WHERE a.user.id = :userId OR t.initiatedByUserId = :userId " +
           "ORDER BY t.createdAt DESC")
    List<LedgerTransaction> findHistoryForUser(@Param("userId") Long userId);
}