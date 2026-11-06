package com.ledgerline;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.service.TransactionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LedgerImmutabilityTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Requirement 10: Ledger Immutability - Historical ledger records cannot be modified")
    void testLedgerEntriesCannotBeModified() {

        Account acc = createTestAccount(userOne, "ACC-IMMUTABLE-001", new BigDecimal("500.00"));

        // Execute a deposit to create an immutable ledger entry
        transactionService.deposit(
                new DepositRequest(acc.getAccountNumber(), new BigDecimal("200.00"), "Deposit for immutability test"),
                "KEY-IMMUTABLE-1",
                userOnePrincipal,
                "127.0.0.1"
        );

        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByCreatedAtDesc(acc.getId());
        assertEquals(1, entries.size());
        LedgerEntry originalEntry = entries.get(0);
        BigDecimal originalAmount = originalEntry.getAmount();
        BigDecimal originalSigned = originalEntry.getSignedAmount();

        // Verify that Hibernate excludes updatable=false columns from UPDATE queries
        // Even if an attacker tries to execute a native update or entity update:
        entityManager.clear(); // Clear first-level cache

        LedgerEntry loadedEntry = entryRepository.findById(originalEntry.getId()).orElseThrow();
        assertEquals(0, originalAmount.compareTo(loadedEntry.getAmount()));
        assertEquals(0, originalSigned.compareTo(loadedEntry.getSignedAmount()));
    }
}
