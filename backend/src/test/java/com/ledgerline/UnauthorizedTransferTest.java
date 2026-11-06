package com.ledgerline;

import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AuditLog;
import com.ledgerline.enums.AuditEventType;
import com.ledgerline.exception.UnauthorizedAccountAccessException;
import com.ledgerline.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UnauthorizedTransferTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Requirement 8: Unauthorized Transfer - User cannot withdraw or transfer from accounts they do not own")
    void testUserCannotTransferFromOtherUsersAccount() {
        Account userTwoAccount = createTestAccount(userTwo, "ACC-VICTIM-001", new BigDecimal("1000.00"));
        Account userOneAccount = createTestAccount(userOne, "ACC-ATTACKER-001", new BigDecimal("100.00"));

        // User 1 tries to initiate a transfer taking money FROM userTwoAccount
        assertThrows(UnauthorizedAccountAccessException.class, () -> {
            transactionService.transfer(
                    new TransferRequest(
                            userTwoAccount.getAccountNumber(),
                            userOneAccount.getAccountNumber(),
                            new BigDecimal("500.00"),
                            "Unauthorized transfer attempt"
                    ),
                    "KEY-ATTACK-001",
                    userOnePrincipal, // Principal is User 1!
                    "10.0.0.99"
            );
        });

        // Assert: Victim balance must remain completely untouched
        Account victimReloaded = accountRepository.findByAccountNumber(userTwoAccount.getAccountNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(victimReloaded.getBalance()));

        // Assert: Attacker balance must remain untouched
        Account attackerReloaded = accountRepository.findByAccountNumber(userOneAccount.getAccountNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(attackerReloaded.getBalance()));

        // Assert: Audit log records the failed transfer attempt
        List<AuditLog> auditLogs = auditLogRepository.findByUsernameOrderByTimestampDesc(userOne.getUsername());
        assertFalse(auditLogs.isEmpty());
        AuditLog logEntry = auditLogs.get(0);
        assertEquals(AuditEventType.TRANSFER_FAILED.name(), logEntry.getEventType());
        assertEquals("FAILURE", logEntry.getStatus());
        assertTrue(logEntry.getFailureReason().contains("not authorized"));
    }

    @Test
    @DisplayName("Requirement 8b: Unauthorized Withdrawal - User cannot withdraw from another user's account")
    void testUserCannotWithdrawFromOtherUsersAccount() {
        Account userTwoAccount = createTestAccount(userTwo, "ACC-VICTIM-002", new BigDecimal("1000.00"));

        assertThrows(UnauthorizedAccountAccessException.class, () -> {
            transactionService.withdraw(
                    new WithdrawRequest(userTwoAccount.getAccountNumber(), new BigDecimal("200.00"), "Theft attempt"),
                    "KEY-ATTACK-002",
                    userOnePrincipal,
                    "10.0.0.99"
            );
        });

        Account victimReloaded = accountRepository.findByAccountNumber(userTwoAccount.getAccountNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(victimReloaded.getBalance()));
    }
}
