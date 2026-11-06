package com.ledgerline.service;

import com.ledgerline.dto.LedgerEntryResponse;
import com.ledgerline.dto.StatementResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.exception.AccountNotFoundException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountService accountService;

    public StatementService(AccountRepository accountRepository, LedgerEntryRepository entryRepository, AccountService accountService) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public StatementResponse getStatement(String accountNumber, LocalDate startDate, LocalDate endDate, UserPrincipal principal) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

        accountService.validateAccountAccess(account, principal);

        List<LedgerEntry> entries = entryRepository.findByAccountIdOrderByCreatedAtDesc(account.getId());

        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : LocalDateTime.MIN;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.MAX;

        List<LedgerEntryResponse> entryResponses = entries.stream()
                .filter(e -> !e.getCreatedAt().isBefore(startDateTime) && !e.getCreatedAt().isAfter(endDateTime))
                .map(e -> new LedgerEntryResponse(
                        e.getId(),
                        e.getLedgerTransaction().getTransactionReference(),
                        e.getAccount().getAccountNumber(),
                        e.getEntryType(),
                        e.getAmount(),
                        e.getSignedAmount(),
                        e.getBalanceAfter(),
                        e.getLedgerTransaction().getDescription(),
                        e.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new StatementResponse(
                account.getAccountNumber(),
                account.getAccountName(),
                account.getCurrency(),
                account.getBalance(),
                startDate != null ? startDate : LocalDate.now().minusMonths(1),
                endDate != null ? endDate : LocalDate.now(),
                entryResponses
        );
    }
}
