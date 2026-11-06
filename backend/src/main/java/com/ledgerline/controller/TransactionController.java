package com.ledgerline.controller;

import com.ledgerline.dto.*;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.TransactionService;
import com.ledgerline.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final LedgerTransactionRepository transactionRepository;
    private final SecurityUtils securityUtils;

    public TransactionController(TransactionService transactionService, LedgerTransactionRepository transactionRepository, SecurityUtils securityUtils) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.securityUtils = securityUtils;
    }

    @PostMapping("/deposit")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        String clientIp = getClientIp(servletRequest);
        TransactionResponse response = transactionService.deposit(request, idempotencyKey, activePrincipal, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully", response));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        String clientIp = getClientIp(servletRequest);
        TransactionResponse response = transactionService.withdraw(request, idempotencyKey, activePrincipal, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal processed successfully", response));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        String clientIp = getClientIp(servletRequest);
        TransactionResponse response = transactionService.transfer(request, idempotencyKey, activePrincipal, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Transfer processed successfully", response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        List<LedgerTransaction> txns;
        if (activePrincipal.getRole() == com.ledgerline.enums.Role.ROLE_ADMIN || activePrincipal.getRole() == com.ledgerline.enums.Role.ROLE_AUDITOR) {
            txns = transactionRepository.findAllByOrderByCreatedAtDesc();
        } else {
            txns = transactionRepository.findByInitiatedByUserIdOrderByCreatedAtDesc(activePrincipal.getId());
        }

        List<TransactionResponse> responseList = txns.stream()
                .map(t -> new TransactionResponse(
                        t.getTransactionReference(),
                        t.getTransactionType(),
                        t.getAmount(),
                        t.getCurrency(),
                        t.getStatus(),
                        t.getDescription(),
                        null,
                        null,
                        t.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved", responseList));
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
