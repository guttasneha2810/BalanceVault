package com.ledgerline;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdempotencySimultaneousTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Requirement 6: Simultaneous Requests with Same Key - Only one executes, balance updated once")
    void testSimultaneousRequestsWithSameKey() throws InterruptedException {
        Account acc = createTestAccount(userOne, "ACC-SIMULT-001", new BigDecimal("100.00"));
        String sharedKey = "SIMULTANEOUS-KEY-001";
        DepositRequest request = new DepositRequest(acc.getAccountNumber(), new BigDecimal("50.00"), "Simultaneous deposit");

        int concurrency = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    transactionService.deposit(
                            request,
                            sharedKey,
                            userOnePrincipal,
                            "127.0.0.1"
                    );
                    successCount.incrementAndGet();
                } catch (IdempotencyConflictException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected error
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        doneGate.await();
        executor.shutdown();

        // One must succeed; the other gets conflict or cached result
        Account finalAccount = accountRepository.findByAccountNumber(acc.getAccountNumber()).orElseThrow();

        // Critical assertion: Deposit of $50 happened ONCE, balance is strictly $150.00
        assertEquals(0, new BigDecimal("150.00").compareTo(finalAccount.getBalance()),
                "Balance must strictly be 150.00 (not 200.00). Duplicate operation was prevented!");
    }
}
