package com.ledgerline;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MoneyConservationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Requirement 1: Strict Money Conservation - All ledger transaction entries sum to zero")
    void testMoneyConservationAcrossMultipleOperations() {
        // Create accounts
        Account acc1 = createTestAccount(userOne, "ACC-TEST-001", new BigDecimal("1000.00"));
        Account acc2 = createTestAccount(userTwo, "ACC-TEST-002", new BigDecimal("500.00"));

        // 1. Deposit $300 into ACC-TEST-001
        transactionService.deposit(
                new DepositRequest(acc1.getAccountNumber(), new BigDecimal("300.00"), "Test Deposit"),
                "KEY-DEP-1",
                userOnePrincipal,
                "127.0.0.1"
        );

        // 2. Transfer $400 from ACC-TEST-001 to ACC-TEST-002
        transactionService.transfer(
                new TransferRequest(acc1.getAccountNumber(), acc2.getAccountNumber(), new BigDecimal("400.00"), "Test Transfer"),
                "KEY-TRF-1",
                userOnePrincipal,
                "127.0.0.1"
        );

        // 3. Withdraw $150 from ACC-TEST-002
        transactionService.withdraw(
                new WithdrawRequest(acc2.getAccountNumber(), new BigDecimal("150.00"), "Test Withdrawal"),
                "KEY-WTH-1",
                userTwoPrincipal,
                "127.0.0.1"
        );

        // Assert: Invariant 1 - Every single transaction in the database must have entries that sum to 0.0000
        List<LedgerTransaction> allTxns = transactionRepository.findAll();
        assertEquals(3, allTxns.size(), "Should have exactly 3 completed transactions");

        for (LedgerTransaction txn : allTxns) {
            BigDecimal sum = entryRepository.sumSignedAmountByTransactionId(txn.getId());
            assertEquals(0, BigDecimal.ZERO.compareTo(sum),
                    "Transaction " + txn.getTransactionReference() + " entries must sum to exactly zero!");
        }

        // Assert: Invariant 2 - Balances match expected
        Account updatedAcc1 = accountRepository.findByAccountNumber(acc1.getAccountNumber()).orElseThrow();
        Account updatedAcc2 = accountRepository.findByAccountNumber(acc2.getAccountNumber()).orElseThrow();

        // ACC1: 1000 + 300 - 400 = 900.00
        assertEquals(0, new BigDecimal("900.00").compareTo(updatedAcc1.getBalance()));
        // ACC2: 500 + 400 - 150 = 750.00
        assertEquals(0, new BigDecimal("750.00").compareTo(updatedAcc2.getBalance()));
    }
}
