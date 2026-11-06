package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.AuditLogResponse;
import com.ledgerline.entity.AuditLog;
import com.ledgerline.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAllAuditLogs() {
        List<AuditLogResponse> logs = auditLogService.getAllAuditLogs().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved", logs));
    }

    @GetMapping("/logs/user/{username}")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByUser(@PathVariable String username) {
        List<AuditLogResponse> logs = auditLogService.getAuditLogsByUsername(username).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("User audit logs retrieved", logs));
    }

    @GetMapping("/logs/account/{accountNumber}")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getLogsByAccount(@PathVariable String accountNumber) {
        List<AuditLogResponse> logs = auditLogService.getAuditLogsByAccountNumber(accountNumber).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Account audit logs retrieved", logs));
    }

    private AuditLogResponse mapToResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEventType(),
                log.getUsername(),
                log.getClientIp(),
                log.getAccountNumber(),
                log.getTransactionReference(),
                log.getStatus(),
                log.getFailureReason(),
                log.getDetails(),
                log.getTimestamp()
        );
    }
}
