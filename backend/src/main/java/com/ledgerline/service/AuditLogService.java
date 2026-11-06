package com.ledgerline.service;

import com.ledgerline.entity.AuditLog;
import com.ledgerline.enums.AuditEventType;
import com.ledgerline.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(
            AuditEventType eventType,
            String username,
            String clientIp,
            String accountNumber,
            String transactionReference,
            String status,
            String failureReason,
            String details) {
        try {
            AuditLog auditLog = new AuditLog(
                    eventType.name(),
                    username,
                    clientIp,
                    accountNumber,
                    transactionReference,
                    status,
                    failureReason,
                    details
            );
            auditLogRepository.save(auditLog);
            log.info("Audit event logged: [type={}, user={}, status={}, ref={}, reason={}]",
                    eventType, username, status, transactionReference, failureReason);
        } catch (Exception ex) {
            log.error("Failed to write audit log: {}", ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByUsername(String username) {
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByAccountNumber(String accountNumber) {
        return auditLogRepository.findByAccountNumberOrderByTimestampDesc(accountNumber);
    }
}
