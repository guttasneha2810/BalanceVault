package com.ledgerline.controller;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.UserResponse;
import com.ledgerline.entity.User;
import com.ledgerline.enums.AccountStatus;
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

    public AdminController(
            UserRepository userRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.auditLogService = auditLogService;
    }

    // Get all users
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {

        List<UserResponse> users = userRepository.findAll().stream()
                .map(this::convertToUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Users retrieved", users)
        );
    }

    // Get users waiting for admin approval
    @GetMapping("/users/pending")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getPendingUsers() {

        List<UserResponse> users = userRepository.findByStatus("PENDING")
                .stream()
                .map(this::convertToUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Pending users retrieved", users)
        );
    }

    // Approve or reject a registration
    @PatchMapping("/users/{id}/approval")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserApproval(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String newStatus = body.get("status");

        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("Approval status is required");
        }

        newStatus = newStatus.trim().toUpperCase();

        if (!newStatus.equals("APPROVED")
                && !newStatus.equals("REJECTED")) {
            throw new IllegalArgumentException(
                    "Status must be APPROVED or REJECTED"
            );
        }

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        if (user.getRole().name().equals("ROLE_ADMIN")
                || user.getRole().name().equals("ROLE_AUDITOR")) {
            throw new IllegalArgumentException(
                    "Admin approval is only available for account holders"
            );
        }

        if (!"PENDING".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException(
                    "This user is not waiting for approval"
            );
        }

        user.setStatus(newStatus);
        User updatedUser = userRepository.save(user);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "User " + newStatus.toLowerCase() + " successfully",
                        convertToUserResponse(updatedUser)
                )
        );
    }

    // Get all accounts
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {

        List<AccountResponse> accounts = accountService.getAllAccounts();

        return ResponseEntity.ok(
                ApiResponse.success("All accounts retrieved", accounts)
        );
    }

    // Update account status - existing functionality preserved
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

        AccountStatus newStatus =
                AccountStatus.valueOf(statusStr.toUpperCase());

        AccountResponse updated =
                accountService.updateAccountStatus(id, newStatus);

        auditLogService.logEvent(
                com.ledgerline.enums.AuditEventType.ACCOUNT_STATUS_CHANGED,
                principal.getUsername(),
                servletRequest.getRemoteAddr(),
                updated.getAccountNumber(),
                null,
                "SUCCESS",
                null,
                "Admin updated account status to " + newStatus
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Account status updated to " + newStatus,
                        updated
                )
        );
    }

    // Convert User entity into response DTO
    private UserResponse convertToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
