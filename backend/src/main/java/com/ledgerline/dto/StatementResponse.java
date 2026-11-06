package com.ledgerline.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class StatementResponse {

    private String accountNumber;
    private String accountName;
    private String currency;
    private BigDecimal currentBalance;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<LedgerEntryResponse> entries;

    public StatementResponse() {
    }

    public StatementResponse(String accountNumber, String accountName, String currency, BigDecimal currentBalance, LocalDate startDate, LocalDate endDate, List<LedgerEntryResponse> entries) {
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.currency = currency;
        this.currentBalance = currentBalance;
        this.startDate = startDate;
        this.endDate = endDate;
        this.entries = entries;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<LedgerEntryResponse> getEntries() {
        return entries;
    }

    public void setEntries(List<LedgerEntryResponse> entries) {
        this.entries = entries;
    }
}
