package com.ledgerline;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class IdempotencyDuplicateTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Requirement 5: Duplicate Idempotency Requests - Cached result returned, zero double execution")
    void testSequentialDuplicateIdempotencyKey() {
        Account acc = createTestAccount(userOne, "ACC-IDEMP-001", new BigDecimal("50.00"));
        String idempotencyKey = "IDEMP-KEY-TEST-001";

        DepositRequest request = new DepositRequest(acc.getAccountNumber(), new BigDecimal("100.00"), "First Deposit");

        // First attempt: executes deposit
        TransactionResponse firstResponse = transactionService.deposit(
                request,
                idempotencyKey,
                userOnePrincipal,
                "127.0.0.1"
        );
        assertNotNull(firstResponse);
        assertNotNull(firstResponse.getTransactionReference());

        Account afterFirst = accountRepository.findByAccountNumber(acc.getAccountNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(afterFirst.getBalance()), "Balance should be 50 + 100 = 150.00");

        // Second attempt: identical request and idempotency key
        TransactionResponse secondResponse = transactionService.deposit(
                request,
                idempotencyKey,
                userOnePrincipal,
                "127.0.0.1"
        );

        // Must return identical cached transaction reference
        assertEquals(firstResponse.getTransactionReference(), secondResponse.getTransactionReference());

        // Balance must STILL be $150.00 (NOT $250.00!)
        Account afterSecond = accountRepository.findByAccountNumber(acc.getAccountNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(afterSecond.getBalance()),
                "Balance must remain 150.00; duplicate execution must be prevented");
    }

    @Test
    @DisplayName("Requirement 5b: Idempotency Key Reuse With Different Payload Must Be Rejected")
    void testIdempotencyKeyReuseWithModifiedPayloadRejection() {
        Account acc = createTestAccount(userOne, "ACC-IDEMP-002", new BigDecimal("50.00"));
        String idempotencyKey = "IDEMP-KEY-TEST-002";

        DepositRequest originalRequest = new DepositRequest(acc.getAccountNumber(), new BigDecimal("100.00"), "Deposit A");

        transactionService.deposit(
                originalRequest,
                idempotencyKey,
                userOnePrincipal,
                "127.0.0.1"
        );

        // Reusing same key with DIFFERENT payload ($200 instead of $100)
        DepositRequest modifiedRequest = new DepositRequest(acc.getAccountNumber(), new BigDecimal("200.00"), "Deposit B");

        assertThrows(IdempotencyConflictException.class, () -> {
            transactionService.deposit(
                    modifiedRequest,
                    idempotencyKey,
                    userOnePrincipal,
                    "127.0.0.1"
            );
        });
    }
}
