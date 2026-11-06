package com.ledgerline.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private String eventType;

    @Column(name = "username", length = 50, updatable = false)
    private String username;

    @Column(name = "client_ip", length = 45, updatable = false)
    private String clientIp;

    @Column(name = "account_number", length = 34, updatable = false)
    private String accountNumber;

    @Column(name = "transaction_reference", length = 64, updatable = false)
    private String transactionReference;

    @NotBlank
    @Size(max = 20)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private String status; // 'SUCCESS' or 'FAILURE'

    @Column(name = "failure_reason", length = 255, updatable = false)
    private String failureReason;

    @Lob
    @Column(name = "details", columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public AuditLog() {
    }

    public AuditLog(String eventType, String username, String clientIp, String accountNumber, String transactionReference, String status, String failureReason, String details) {
        this.eventType = eventType;
        this.username = username;
        this.clientIp = clientIp;
        this.accountNumber = accountNumber;
        this.transactionReference = transactionReference;
        this.status = status;
        this.failureReason = failureReason;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters only for immutability
    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUsername() {
        return username;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
