package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.StatementResponse;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.StatementService;
import com.ledgerline.util.SecurityUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;
    private final SecurityUtils securityUtils;

    public StatementController(StatementService statementService, SecurityUtils securityUtils) {
        this.statementService = statementService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<StatementResponse>> getAccountStatement(
            @PathVariable String accountNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {

        UserPrincipal activePrincipal = securityUtils.getCurrentPrincipal(principal);
        StatementResponse statement = statementService.getStatement(accountNumber, startDate, endDate, activePrincipal);
        return ResponseEntity.ok(ApiResponse.success("Account statement generated", statement));
    }
}
