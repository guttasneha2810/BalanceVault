package com.ledgerline.dto;

import com.ledgerline.enums.EntryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LedgerEntryResponse {

    private Long id;
    private String transactionReference;
    private String accountNumber;
    private EntryType entryType;
    private BigDecimal amount;
    private BigDecimal signedAmount;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime timestamp;

    public LedgerEntryResponse() {
    }

    public LedgerEntryResponse(Long id, String transactionReference, String accountNumber, EntryType entryType, BigDecimal amount, BigDecimal signedAmount, BigDecimal balanceAfter, String description, LocalDateTime timestamp) {
        this.id = id;
        this.transactionReference = transactionReference;
        this.accountNumber = accountNumber;
        this.entryType = entryType;
        this.amount = amount;
        this.signedAmount = signedAmount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getSignedAmount() {
        return signedAmount;
    }

    public void setSignedAmount(BigDecimal signedAmount) {
        this.signedAmount = signedAmount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
