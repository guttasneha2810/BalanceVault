package com.ledgerline.controller;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.UserResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.User;
import com.ledgerline.enums.AccountStatus;
import com.ledgerline.enums.AuditEventType;
import com.ledgerline.exception.AccountNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AccountService;
import com.ledgerline.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final AuditLogService auditLogService;

    public AdminController(UserRepository userRepository, AccountRepository accountRepository, AccountService accountService, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(u -> new UserResponse(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getFullName(),
                        u.getRole(),
                        u.getStatus(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Users retrieved", users));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {
        List<AccountResponse> accounts = accountService.getAllAccounts();

        return ResponseEntity.ok(
                ApiResponse.success("All accounts retrieved", accounts)
        );
    }

    @PatchMapping("/accounts/{id}/status")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccountStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        AccountStatus newStatus = AccountStatus.valueOf(statusStr.toUpperCase());
        AccountResponse updated = accountService.updateAccountStatus(id, newStatus);

        auditLogService.logEvent(
                AuditEventType.ACCOUNT_STATUS_CHANGED,
                principal.getUsername(),
                servletRequest.getRemoteAddr(),
                updated.getAccountNumber(),
                null,
                "SUCCESS",
                null,
                "Admin updated account status to " + newStatus
        );

        return ResponseEntity.ok(ApiResponse.success("Account status updated to " + newStatus, updated));
    }
}
