package com.ledgerline;

import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.exception.InsufficientFundsException;
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

public class ConcurrentWithdrawalTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Requirement 3: Concurrent Withdrawals - Race condition protection under multi-threaded contention")
    void testConcurrentWithdrawalsNeverOverdraw() throws InterruptedException {
        // Starting balance: $500.00
        Account acc = createTestAccount(userOne, "ACC-CONCUR-WTH", new BigDecimal("500.00"));

        int numThreads = 10;
        BigDecimal withdrawAmount = new BigDecimal("200.00");
        // Only 2 withdrawals of $200 can succeed out of $500! The other 8 must fail.

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final String key = "CONCUR-WTH-KEY-" + i;
            executor.submit(() -> {
                try {
                    startGate.await(); // Wait for simultaneous start signal
                    transactionService.withdraw(
                            new WithdrawRequest(acc.getAccountNumber(), withdrawAmount, "Concurrent withdrawal"),
                            key,
                            userOnePrincipal,
                            "127.0.0.1"
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Trigger all threads simultaneously
        startGate.countDown();
        doneGate.await();
        executor.shutdown();

        // Exactly 2 threads succeed: 2 * $200 = $400
        assertEquals(2, successCount.get(), "Exactly 2 withdrawals should succeed");
        assertEquals(8, failureCount.get(), "Exactly 8 withdrawals should fail due to insufficient funds");

        Account finalAccount = accountRepository.findByAccountNumber(acc.getAccountNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(finalAccount.getBalance()),
                "Final balance must be exactly $100.00 (500 - 2*200)");
    }
}
