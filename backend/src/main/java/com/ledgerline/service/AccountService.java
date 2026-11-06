package com.ledgerline.service;

import com.ledgerline.dto.AccountCreateRequest;
import com.ledgerline.dto.AccountResponse;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.User;
import com.ledgerline.enums.AccountStatus;
import com.ledgerline.enums.Role;
import com.ledgerline.exception.AccountNotFoundException;
import com.ledgerline.exception.UnauthorizedAccountAccessException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.util.AccountNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request, UserPrincipal principal) {
        Long targetUserId;
        if (principal.getRole() == Role.ROLE_ADMIN && request.getUserId() != null) {
            targetUserId = request.getUserId();
        } else {
            targetUserId = principal.getId();
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + targetUserId));

        String accountNumber = generateUniqueAccountNumber();

        BigDecimal initialDeposit = request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO;

        Account account = new Account(
                accountNumber,
                user,
                request.getAccountName(),
                request.getAccountType(),
                initialDeposit,
                request.getCurrency() != null ? request.getCurrency() : "USD"
        );

        Account saved = accountRepository.save(account);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAuthorizedAccounts(UserPrincipal principal) {
        if (principal.getRole() == Role.ROLE_ADMIN || principal.getRole() == Role.ROLE_AUDITOR) {
            return accountRepository.findByIsSystemAccountFalse().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        return accountRepository.findByUserId(principal.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber, UserPrincipal principal) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountNumber));

        validateAccountAccess(account, principal);
        return mapToResponse(account);
    }

    @Transactional
    public AccountResponse updateAccountStatus(Long accountId, AccountStatus status) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + accountId));

        account.setStatus(status);
        Account updated = accountRepository.save(account);
        return mapToResponse(updated);
    }

    public void validateAccountAccess(Account account, UserPrincipal principal) {
        if (principal.getRole() == Role.ROLE_ADMIN || principal.getRole() == Role.ROLE_AUDITOR) {
            return; // Admins and auditors can view all accounts
        }
        if (account.getUser() == null || !account.getUser().getId().equals(principal.getId())) {
            throw new UnauthorizedAccountAccessException("You do not have permission to access account: " + account.getAccountNumber());
        }
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            accountNumber = AccountNumberGenerator.generate();
        } while (accountRepository.findByAccountNumber(accountNumber).isPresent());
        return accountNumber;
    }

    public AccountResponse mapToResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountName(),
                account.getAccountType(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getUser() != null ? account.getUser().getId() : null,
                account.getUser() != null ? account.getUser().getFullName() : "SYSTEM",
                account.isSystemAccount(),
                account.getCreatedAt()
        );
    }
}
