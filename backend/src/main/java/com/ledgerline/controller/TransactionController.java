package com.ledgerline.controller;

import com.ledgerline.dto.*;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.repository.LedgerTransactionRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.TransactionService;
import com.ledgerline.util.SecurityUtils;
import com.ledgerline.enums.Role;
import com.ledgerline.enums.EntryType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final LedgerTransactionRepository transactionRepository;
    private final SecurityUtils securityUtils;
    private final LedgerEntryRepository ledgerEntryRepository;

    // Constructor
    public TransactionController(
            TransactionService transactionService,
            LedgerTransactionRepository transactionRepository,
            SecurityUtils securityUtils,
            LedgerEntryRepository ledgerEntryRepository) {

        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.securityUtils = securityUtils;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    // Deposit
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        UserPrincipal activePrincipal =
                securityUtils.getCurrentPrincipal(principal);

        String clientIp = getClientIp(servletRequest);

        TransactionResponse response = transactionService.deposit(
                request, idempotencyKey, activePrincipal, clientIp);

        return ResponseEntity.ok(
                ApiResponse.success("Deposit processed successfully", response));
    }

    // Withdrawal
    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        UserPrincipal activePrincipal =
                securityUtils.getCurrentPrincipal(principal);

        String clientIp = getClientIp(servletRequest);

        TransactionResponse response = transactionService.withdraw(
                request, idempotencyKey, activePrincipal, clientIp);

        return ResponseEntity.ok(
                ApiResponse.success("Withdrawal processed successfully", response));
    }

    // Transfer
    @PostMapping("/transfer")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {

        UserPrincipal activePrincipal =
                securityUtils.getCurrentPrincipal(principal);

        String clientIp = getClientIp(servletRequest);

        TransactionResponse response = transactionService.transfer(
                request, idempotencyKey, activePrincipal, clientIp);

        return ResponseEntity.ok(
                ApiResponse.success("Transfer processed successfully", response));
    }

    // Transaction History
    @GetMapping("/history")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(
            @AuthenticationPrincipal UserPrincipal principal) {

        UserPrincipal activePrincipal =
                securityUtils.getCurrentPrincipal(principal);

        List<LedgerTransaction> txns;

        boolean isAdminOrAuditor =
                activePrincipal.getRole() == Role.ROLE_ADMIN ||
                activePrincipal.getRole() == Role.ROLE_AUDITOR;

        if (isAdminOrAuditor) {
            txns = transactionRepository.findAllByOrderByCreatedAtDesc();
        } else {
            txns = transactionRepository.findHistoryForUser(
                    activePrincipal.getId());
        }

        List<TransactionResponse> responseList = txns.stream()
                .map(t -> {

                    List<LedgerEntry> entries =
                            ledgerEntryRepository.findByLedgerTransactionId(t.getId());

                    String sourceAccount = entries.stream()
                            .filter(e -> e.getEntryType() == EntryType.DEBIT)
                            .map(e -> e.getAccount().getAccountNumber())
                            .findFirst()
                            .orElse(null);

                    String destinationAccount = entries.stream()
                            .filter(e -> e.getEntryType() == EntryType.CREDIT)
                            .map(e -> e.getAccount().getAccountNumber())
                            .findFirst()
                            .orElse(null);

                    BigDecimal signedAmount = null;

                    if (!isAdminOrAuditor) {
                        signedAmount = entries.stream()
                                .filter(e -> e.getAccount().getUser() != null &&
                                        e.getAccount().getUser().getId()
                                                .equals(activePrincipal.getId()))
                                .map(LedgerEntry::getSignedAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }

                    TransactionResponse response = new TransactionResponse(
                            t.getTransactionReference(),
                            t.getTransactionType(),
                            t.getAmount(),
                            t.getCurrency(),
                            t.getStatus(),
                            t.getDescription(),
                            sourceAccount,
                            destinationAccount,
                            t.getCreatedAt()
                    );

                    response.setSignedAmount(signedAmount);

                    return response;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Transaction history retrieved",
                        responseList));
    }

    // Get Client IP
    private String getClientIp(HttpServletRequest request) {

        String xf = request.getHeader("X-Forwarded-For");

        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}