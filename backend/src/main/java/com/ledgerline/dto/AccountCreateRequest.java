package com.ledgerline.dto;

import com.ledgerline.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class AccountCreateRequest {

    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name cannot exceed 100 characters")
    private String accountName;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
    private BigDecimal initialDeposit = BigDecimal.ZERO;

    private String currency = "USD";

    private Long userId; // Optional: Admin can specify target user; for account holder, inferred from auth

    public AccountCreateRequest() {
    }

    public AccountCreateRequest(String accountName, AccountType accountType, BigDecimal initialDeposit, String currency) {
        this.accountName = accountName;
        this.accountType = accountType;
        this.initialDeposit = initialDeposit != null ? initialDeposit : BigDecimal.ZERO;
        this.currency = currency != null ? currency : "USD";
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getInitialDeposit() {
        return initialDeposit;
    }

    public void setInitialDeposit(BigDecimal initialDeposit) {
        this.initialDeposit = initialDeposit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
