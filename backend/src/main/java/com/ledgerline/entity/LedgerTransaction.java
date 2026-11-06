package com.ledgerline.entity;

import com.ledgerline.enums.TransactionStatus;
import com.ledgerline.enums.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "transaction_reference", nullable = false, unique = true, length = 64)
    private String transactionReference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @NotNull
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotBlank
    @Size(max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.COMPLETED;

    @NotBlank
    @Size(max = 255)
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "initiated_by_user_id")
    private Long initiatedByUserId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public LedgerTransaction() {
    }

    public LedgerTransaction(String transactionReference, TransactionType transactionType, BigDecimal amount, String currency, String description, Long initiatedByUserId, String idempotencyKey) {
        this.transactionReference = transactionReference;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.status = TransactionStatus.COMPLETED;
        this.description = description;
        this.initiatedByUserId = initiatedByUserId;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getInitiatedByUserId() {
        return initiatedByUserId;
    }

    public void setInitiatedByUserId(Long initiatedByUserId) {
        this.initiatedByUserId = initiatedByUserId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
