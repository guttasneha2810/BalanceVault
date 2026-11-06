package com.ledgerline;

import com.ledgerline.dto.TransferRequest;
import com.ledgerline.entity.Account;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentTransferTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Requirement 4: Concurrent Transfers - Deadlock freedom via ascending ID locking and money conservation")
    void testBidirectionalConcurrentTransfers() throws InterruptedException {
        // Initial balances: $1,000.00 each -> Total = $2,000.00
        Account accA = createTestAccount(userOne, "ACC-TRF-A", new BigDecimal("1000.00"));
        Account accB = createTestAccount(userTwo, "ACC-TRF-B", new BigDecimal("1000.00"));

        int transfersPerDirection = 4;
        int totalThreads = transfersPerDirection * 2;
        BigDecimal transferAmount = new BigDecimal("50.00");

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 4 concurrent transfers A -> B
        for (int i = 0; i < transfersPerDirection; i++) {
            final String key = "TRF-A-TO-B-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    transactionService.transfer(
                            new TransferRequest(accA.getAccountNumber(), accB.getAccountNumber(), transferAmount, "A to B"),
                            key,
                            userOnePrincipal,
                            "127.0.0.1"
                    );
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // 4 concurrent transfers B -> A simultaneously
        for (int i = 0; i < transfersPerDirection; i++) {
            final String key = "TRF-B-TO-A-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    transactionService.transfer(
                            new TransferRequest(accB.getAccountNumber(), accA.getAccountNumber(), transferAmount, "B to A"),
                            key,
                            userTwoPrincipal,
                            "127.0.0.1"
                    );
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        doneGate.await();
        executor.shutdown();

        // Deadlock-free: All concurrent transfers succeed without deadlock!
        assertEquals(totalThreads, successCount.get(), "All concurrent transfers should succeed without deadlock");
        assertEquals(0, failureCount.get(), "No transfers should encounter deadlocks or failures");

        Account reloadedA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        Account reloadedB = accountRepository.findByAccountNumber(accB.getAccountNumber()).orElseThrow();
        BigDecimal totalBalance = reloadedA.getBalance().add(reloadedB.getBalance());
        assertEquals(0, new BigDecimal("2000.00").compareTo(totalBalance), "Total money must be strictly conserved at $2000.00");
        assertTrue(reloadedA.getBalance().compareTo(BigDecimal.ZERO) > 0, "Account A balance must remain positive");
        assertTrue(reloadedB.getBalance().compareTo(BigDecimal.ZERO) > 0, "Account B balance must remain positive");




    }
}
