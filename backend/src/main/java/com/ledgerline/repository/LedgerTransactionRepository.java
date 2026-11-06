package com.ledgerline.repository;

import com.ledgerline.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByTransactionReference(String transactionReference);

    List<LedgerTransaction> findAllByOrderByCreatedAtDesc();

    List<LedgerTransaction> findByInitiatedByUserIdOrderByCreatedAtDesc(Long initiatedByUserId);
}
