package com.ledgerline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class AuditorReadOnlyTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Requirement 9: Auditor Read-Only - Auditor can read accounts, transactions, and audit logs")
    @WithMockUser(username = "auditor", roles = {"AUDITOR"})
    void testAuditorCanReadData() throws Exception {
        // Can view all accounts
        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk());

        // Can view transactions history
        mockMvc.perform(get("/api/transactions/history"))
                .andExpect(status().isOk());

        // Can view audit logs
        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Requirement 9b: Auditor Read-Only - Auditor is forbidden from financial transactions")
    @WithMockUser(username = "auditor", roles = {"AUDITOR"})
    void testAuditorCannotPerformFinancialOperations() throws Exception {
        // Attempt Deposit -> Must be 403 Forbidden
        mockMvc.perform(post("/api/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "AUDITOR-TEST-KEY-1")
                        .content("{\"accountNumber\":\"ACC100\",\"amount\":100.00}"))
                .andExpect(status().isForbidden());

        // Attempt Withdrawal -> Must be 403 Forbidden
        mockMvc.perform(post("/api/transactions/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "AUDITOR-TEST-KEY-2")
                        .content("{\"accountNumber\":\"ACC100\",\"amount\":50.00}"))
                .andExpect(status().isForbidden());

        // Attempt Transfer -> Must be 403 Forbidden
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "AUDITOR-TEST-KEY-3")
                        .content("{\"sourceAccountNumber\":\"ACC100\",\"destinationAccountNumber\":\"ACC200\",\"amount\":25.00}"))
                .andExpect(status().isForbidden());
    }
}
