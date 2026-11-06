package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.StatementResponse;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.service.StatementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<StatementResponse>> getAccountStatement(
            @PathVariable String accountNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {

        StatementResponse statement = statementService.getStatement(accountNumber, startDate, endDate, principal);
        return ResponseEntity.ok(ApiResponse.success("Account statement generated", statement));
    }
}
