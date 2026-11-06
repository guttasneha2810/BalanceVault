package com.ledgerline;

import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AuditLog;
import com.ledgerline.enums.AuditEventType;
import com.ledgerline.exception.InsufficientFundsException;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NoNegativeBalanceTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Requirement 2: No Negative Balance - Overdraft attempts must be refused atomically")
    void testWithdrawalRefusalOnInsufficientFunds() {
        Account acc = createTestAccount(userOne, "ACC-NEG-001", new BigDecimal("100.00"));

        // Attempt to withdraw $100.01
        assertThrows(InsufficientFundsException.class, () -> {
            transactionService.withdraw(
                    new WithdrawRequest(acc.getAccountNumber(), new BigDecimal("100.01"), "Overdraft attempt"),
                    "KEY-NEG-1",
                    userOnePrincipal,
                    "192.168.1.100"
            );
        });

        // Balance must remain strictly unchanged at 100.00
        Account reloaded = accountRepository.findByAccountNumber(acc.getAccountNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(reloaded.getBalance()));

        // Audit log must record the refused attempt
        List<AuditLog> auditLogs = auditLogRepository.findByAccountNumberOrderByTimestampDesc(acc.getAccountNumber());
        assertFalse(auditLogs.isEmpty());
        AuditLog failedLog = auditLogs.get(0);
        assertEquals(AuditEventType.WITHDRAW_FAILED.name(), failedLog.getEventType());
        assertEquals("FAILURE", failedLog.getStatus());
        assertTrue(failedLog.getFailureReason().contains("Insufficient funds"));
    }

    @Test
    @DisplayName("Requirement 2b: No Negative Balance - Transfer exceeding balance must fail")
    void testTransferRefusalOnInsufficientFunds() {
        Account src = createTestAccount(userOne, "ACC-NEG-002", new BigDecimal("50.00"));
        Account dst = createTestAccount(userTwo, "ACC-NEG-003", new BigDecimal("200.00"));

        assertThrows(InsufficientFundsException.class, () -> {
            transactionService.transfer(
                    new TransferRequest(src.getAccountNumber(), dst.getAccountNumber(), new BigDecimal("50.01"), "Overdraft transfer"),
                    "KEY-NEG-2",
                    userOnePrincipal,
                    "192.168.1.100"
            );
        });

        // Balances must remain unchanged
        Account srcReloaded = accountRepository.findByAccountNumber(src.getAccountNumber()).orElseThrow();
        Account dstReloaded = accountRepository.findByAccountNumber(dst.getAccountNumber()).orElseThrow();

        assertEquals(0, new BigDecimal("50.00").compareTo(srcReloaded.getBalance()));
        assertEquals(0, new BigDecimal("200.00").compareTo(dstReloaded.getBalance()));
    }
}
