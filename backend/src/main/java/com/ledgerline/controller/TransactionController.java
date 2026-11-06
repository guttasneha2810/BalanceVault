package com.ledgerline.controller;

import com.ledgerline.dto.*;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.TransactionService;
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

    public TransactionController(TransactionService transactionService, LedgerTransactionRepository transactionRepository) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/deposit")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        String clientIp = getClientIp(servletRequest);
        TransactionResponse response = transactionService.deposit(request, idempotencyKey, principal, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully", response));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        String clientIp = getClientIp(servletRequest);
        TransactionResponse response = transactionService.withdraw(request, idempotencyKey, principal, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal processed successfully", response));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        String clientIp = getClientIp(servletRequest);
        TransactionResponse response = transactionService.transfer(request, idempotencyKey, principal, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Transfer processed successfully", response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<LedgerTransaction> txns;
        if (principal.getRole() == com.ledgerline.enums.Role.ROLE_ADMIN || principal.getRole() == com.ledgerline.enums.Role.ROLE_AUDITOR) {
            txns = transactionRepository.findAllByOrderByCreatedAtDesc();
        } else {
            txns = transactionRepository.findByInitiatedByUserIdOrderByCreatedAtDesc(principal.getId());
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
