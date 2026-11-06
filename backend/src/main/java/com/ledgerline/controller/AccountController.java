package com.ledgerline.controller;

import com.ledgerline.dto.AccountCreateRequest;
import com.ledgerline.dto.AccountResponse;
import com.ledgerline.dto.ApiResponse;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.AccountService;
import com.ledgerline.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final SecurityUtils securityUtils;

    public AccountController(AccountService accountService, SecurityUtils securityUtils) {
        this.accountService = accountService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ACCOUNT_HOLDER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody AccountCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        AccountResponse response = accountService.createAccount(request, activePrincipal);
        return ResponseEntity.ok(ApiResponse.success("Account created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAuthorizedAccounts(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        List<AccountResponse> accounts = accountService.getAuthorizedAccounts(activePrincipal);
        return ResponseEntity.ok(ApiResponse.success("Accounts retrieved successfully", accounts));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountDetails(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        AccountResponse account = accountService.getAccountByNumber(accountNumber, activePrincipal);
        return ResponseEntity.ok(ApiResponse.success("Account details retrieved", account));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccountBalance(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        AccountResponse account = accountService.getAccountByNumber(accountNumber, activePrincipal);
        Map<String, Object> data = Map.of(
                "accountNumber", account.getAccountNumber(),
                "balance", account.getBalance(),
                "currency", account.getCurrency(),
                "status", account.getStatus()
        );
        return ResponseEntity.ok(ApiResponse.success("Balance retrieved", data));
    }
}
