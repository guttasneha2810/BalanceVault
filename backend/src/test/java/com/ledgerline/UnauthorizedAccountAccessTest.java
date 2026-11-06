package com.ledgerline;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.exception.UnauthorizedAccountAccessException;
import com.ledgerline.service.AccountService;
import com.ledgerline.service.StatementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class UnauthorizedAccountAccessTest extends BaseIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private StatementService statementService;

    @Test
    @DisplayName("Requirement 7: Unauthorized Account Access - Account holders cannot view other users' accounts")
    void testAccountHolderCannotAccessAnotherUsersAccount() {
        Account userTwoAccount = createTestAccount(userTwo, "ACC-USER2-SECRET", new BigDecimal("5000.00"));

        // User 1 attempts to read User 2's account details -> Throws 403 Forbidden exception
        assertThrows(UnauthorizedAccountAccessException.class, () -> {
            accountService.getAccountByNumber(userTwoAccount.getAccountNumber(), userOnePrincipal);
        });

        // User 1 attempts to generate a statement for User 2's account -> Throws 403 Forbidden exception
        assertThrows(UnauthorizedAccountAccessException.class, () -> {
            statementService.getStatement(userTwoAccount.getAccountNumber(), LocalDate.now().minusDays(7), LocalDate.now(), userOnePrincipal);
        });

        // Conversely, User 2 CAN access their own account
        AccountResponse response = accountService.getAccountByNumber(userTwoAccount.getAccountNumber(), userTwoPrincipal);
        assertNotNull(response);
        assertEquals(userTwoAccount.getAccountNumber(), response.getAccountNumber());

        // Admin CAN access for management purposes
        AccountResponse adminView = accountService.getAccountByNumber(userTwoAccount.getAccountNumber(), adminPrincipal);
        assertNotNull(adminView);

        // Auditor CAN access for compliance purposes
        AccountResponse auditorView = accountService.getAccountByNumber(userTwoAccount.getAccountNumber(), auditorPrincipal);
        assertNotNull(auditorView);
    }
}
