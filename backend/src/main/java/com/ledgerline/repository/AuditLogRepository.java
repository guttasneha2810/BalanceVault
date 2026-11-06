package com.ledgerline.repository;

import com.ledgerline.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByTimestampDesc();

    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    List<AuditLog> findByAccountNumberOrderByTimestampDesc(String accountNumber);
}
