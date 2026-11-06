package com.ledgerline.dto;

import java.time.LocalDateTime;

public class AuditLogResponse {

    private Long id;
    private String eventType;
    private String username;
    private String clientIp;
    private String accountNumber;
    private String transactionReference;
    private String status;
    private String failureReason;
    private String details;
    private LocalDateTime timestamp;

    public AuditLogResponse() {
    }

    public AuditLogResponse(Long id, String eventType, String username, String clientIp, String accountNumber, String transactionReference, String status, String failureReason, String details, LocalDateTime timestamp) {
        this.id = id;
        this.eventType = eventType;
        this.username = username;
        this.clientIp = clientIp;
        this.accountNumber = accountNumber;
        this.transactionReference = transactionReference;
        this.status = status;
        this.failureReason = failureReason;
        this.details = details;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
