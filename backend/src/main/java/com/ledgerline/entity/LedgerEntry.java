package com.ledgerline.entity;

import com.ledgerline.enums.EntryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction ledgerTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType; // DEBIT or CREDIT

    @NotNull
    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @NotNull
    @Column(name = "signed_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal signedAmount; // Negative for DEBIT, Positive for CREDIT

    @NotNull
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public LedgerEntry() {
    }

    public LedgerEntry(LedgerTransaction ledgerTransaction, Account account, EntryType entryType, BigDecimal amount, BigDecimal signedAmount, BigDecimal balanceAfter) {
        this.ledgerTransaction = ledgerTransaction;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.signedAmount = signedAmount;
        this.balanceAfter = balanceAfter;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Immutable getters (no setters for core financial data to guarantee immutability)
    public Long getId() {
        return id;
    }

    public LedgerTransaction getLedgerTransaction() {
        return ledgerTransaction;
    }

    public Account getAccount() {
        return account;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getSignedAmount() {
        return signedAmount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
