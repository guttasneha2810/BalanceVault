package com.ledgerline.service;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.enums.AccountStatus;
import com.ledgerline.enums.AuditEventType;
import com.ledgerline.enums.TransactionStatus;
import com.ledgerline.enums.TransactionType;
import com.ledgerline.exception.*;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.security.UserPrincipal;
import com.ledgerline.util.TransactionReferenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private static final String SYSTEM_DEP_CLEARING = "SYS-EXTERNAL-DEP-001";
    private static final String SYSTEM_WTH_CLEARING = "SYS-EXTERNAL-WTH-001";

    private final AccountRepository accountRepository;
    private final DoubleEntryLedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public TransactionService(
            AccountRepository accountRepository,
            DoubleEntryLedgerService ledgerService,
            IdempotencyService idempotencyService,
            AuditLogService auditLogService,
            PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Executes an external money deposit into an account.
     */
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey, UserPrincipal principal, String clientIp) {
        // 1. Check or register idempotency key (independent transaction)
        Optional<TransactionResponse> cached = idempotencyService.checkOrLock(
                idempotencyKey,
                principal.getId(),
                "/api/transactions/deposit",
                request,
                TransactionResponse.class
        );
        if (cached.isPresent()) {
            return cached.get();
        }

        String ref = TransactionReferenceGenerator.generate("DEP");
        try {
            validateAmount(request.getAmount());

            // 2. Execute financial balance update and ledger entry inside isolated business transaction
            TransactionResponse response = transactionTemplate.execute(status -> {
                Account account = accountRepository.findByAccountNumberWithLock(request.getAccountNumber())
                        .orElseThrow(() -> new AccountNotFoundException("Account not found: " + request.getAccountNumber()));

                validateAccountActive(account);

                Account clearingAccount = getOrCreateClearingAccount(SYSTEM_DEP_CLEARING, "System External Deposit Clearing");

                BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);

                account.setBalance(account.getBalance().add(amount));
                clearingAccount.setBalance(clearingAccount.getBalance().subtract(amount));

                accountRepository.save(account);
                accountRepository.save(clearingAccount);

                LedgerTransaction txn = ledgerService.recordExternalDeposit(
                        ref,
                        clearingAccount,
                        account,
                        amount,
                        account.getCurrency(),
                        request.getDescription(),
                        principal.getId(),
                        idempotencyKey
                );

                return new TransactionResponse(
                        txn.getTransactionReference(),
                        TransactionType.DEPOSIT,
                        amount,
                        account.getCurrency(),
                        TransactionStatus.COMPLETED,
                        request.getDescription(),
                        SYSTEM_DEP_CLEARING,
                        account.getAccountNumber(),
                        txn.getCreatedAt()
                );
            });

            // 3. Record audit and idempotency success
            auditLogService.logEvent(
                    AuditEventType.DEPOSIT_SUCCESS,
                    principal.getUsername(),
                    clientIp,
                    request.getAccountNumber(),
                    ref,
                    "SUCCESS",
                    null,
                    "Deposited " + request.getAmount()
            );

            idempotencyService.recordSuccess(idempotencyKey, 200, response);
            return response;

        } catch (Exception ex) {
            auditLogService.logEvent(
                    AuditEventType.DEPOSIT_FAILED,
                    principal.getUsername(),
                    clientIp,
                    request.getAccountNumber(),
                    ref,
                    "FAILURE",
                    ex.getMessage(),
                    "Deposit failed for amount: " + request.getAmount()
            );
            idempotencyService.recordFailure(idempotencyKey, 400, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Executes an external withdrawal with atomic balance validation under pessimistic lock.
     */
    public TransactionResponse withdraw(WithdrawRequest request, String idempotencyKey, UserPrincipal principal, String clientIp) {
        Optional<TransactionResponse> cached = idempotencyService.checkOrLock(
                idempotencyKey,
                principal.getId(),
                "/api/transactions/withdraw",
                request,
                TransactionResponse.class
        );
        if (cached.isPresent()) {
            return cached.get();
        }

        String ref = TransactionReferenceGenerator.generate("WTH");
        try {
            validateAmount(request.getAmount());

            TransactionResponse response = transactionTemplate.execute(status -> {
                Account account = accountRepository.findByAccountNumberWithLock(request.getAccountNumber())
                        .orElseThrow(() -> new AccountNotFoundException("Account not found: " + request.getAccountNumber()));

                validateOwnership(account, principal);
                validateAccountActive(account);

                BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);

                // Atomic balance verification under row lock
                if (account.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException(String.format(
                            "Insufficient funds in account %s. Current balance: %s, Requested withdrawal: %s",
                            account.getAccountNumber(), account.getBalance(), amount));
                }

                Account clearingAccount = getOrCreateClearingAccount(SYSTEM_WTH_CLEARING, "System External Withdrawal Clearing");

                account.setBalance(account.getBalance().subtract(amount));
                clearingAccount.setBalance(clearingAccount.getBalance().add(amount));

                accountRepository.save(account);
                accountRepository.save(clearingAccount);

                LedgerTransaction txn = ledgerService.recordExternalWithdrawal(
                        ref,
                        account,
                        clearingAccount,
                        amount,
                        account.getCurrency(),
                        request.getDescription(),
                        principal.getId(),
                        idempotencyKey
                );

                return new TransactionResponse(
                        txn.getTransactionReference(),
                        TransactionType.WITHDRAWAL,
                        amount,
                        account.getCurrency(),
                        TransactionStatus.COMPLETED,
                        request.getDescription(),
                        account.getAccountNumber(),
                        SYSTEM_WTH_CLEARING,
                        txn.getCreatedAt()
                );
            });

            auditLogService.logEvent(
                    AuditEventType.WITHDRAW_SUCCESS,
                    principal.getUsername(),
                    clientIp,
                    request.getAccountNumber(),
                    ref,
                    "SUCCESS",
                    null,
                    "Withdrew " + request.getAmount()
            );

            idempotencyService.recordSuccess(idempotencyKey, 200, response);
            return response;

        } catch (Exception ex) {
            auditLogService.logEvent(
                    AuditEventType.WITHDRAW_FAILED,
                    principal.getUsername(),
                    clientIp,
                    request.getAccountNumber(),
                    ref,
                    "FAILURE",
                    ex.getMessage(),
                    "Withdrawal failed for amount: " + request.getAmount()
            );
            idempotencyService.recordFailure(idempotencyKey, 400, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Executes an internal transfer between two accounts with deadlock-free lock ordering.
     */
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey, UserPrincipal principal, String clientIp) {
        Optional<TransactionResponse> cached = idempotencyService.checkOrLock(
                idempotencyKey,
                principal.getId(),
                "/api/transactions/transfer",
                request,
                TransactionResponse.class
        );
        if (cached.isPresent()) {
            return cached.get();
        }

        String ref = TransactionReferenceGenerator.generate("TRF");
        try {
            validateAmount(request.getAmount());

            if (request.getSourceAccountNumber().equalsIgnoreCase(request.getDestinationAccountNumber())) {
                throw new InvalidTransactionException("Source and destination accounts cannot be identical");
            }

            TransactionResponse response = transactionTemplate.execute(status -> {
                // Fetch basic entities to compute ascending locking order
                Account srcBasic = accountRepository.findByAccountNumber(request.getSourceAccountNumber())
                        .orElseThrow(() -> new AccountNotFoundException("Source account not found: " + request.getSourceAccountNumber()));

                Account dstBasic = accountRepository.findByAccountNumber(request.getDestinationAccountNumber())
                        .orElseThrow(() -> new AccountNotFoundException("Destination account not found: " + request.getDestinationAccountNumber()));

                validateOwnership(srcBasic, principal);

                // =========================================================================
                // DEADLOCK PREVENTION STRATEGY: Consistent Ascending Numerical ID Locking
                // =========================================================================
                Long id1 = srcBasic.getId().compareTo(dstBasic.getId()) < 0 ? srcBasic.getId() : dstBasic.getId();
                Long id2 = srcBasic.getId().compareTo(dstBasic.getId()) < 0 ? dstBasic.getId() : srcBasic.getId();

                Account firstLocked = accountRepository.findByIdWithLock(id1)
                        .orElseThrow(() -> new AccountNotFoundException("Account lock acquisition failed for ID: " + id1));
                Account secondLocked = accountRepository.findByIdWithLock(id2)
                        .orElseThrow(() -> new AccountNotFoundException("Account lock acquisition failed for ID: " + id2));

                Account sourceAccount = firstLocked.getId().equals(srcBasic.getId()) ? firstLocked : secondLocked;
                Account destinationAccount = firstLocked.getId().equals(dstBasic.getId()) ? firstLocked : secondLocked;

                validateAccountActive(sourceAccount);
                validateAccountActive(destinationAccount);

                BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);

                // Atomic balance verification under row lock
                if (sourceAccount.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException(String.format(
                            "Insufficient funds in source account %s. Current balance: %s, Transfer amount: %s",
                            sourceAccount.getAccountNumber(), sourceAccount.getBalance(), amount));
                }

                sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
                destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

                accountRepository.save(sourceAccount);
                accountRepository.save(destinationAccount);

                LedgerTransaction txn = ledgerService.recordInternalTransfer(
                        ref,
                        sourceAccount,
                        destinationAccount,
                        amount,
                        sourceAccount.getCurrency(),
                        request.getDescription(),
                        principal.getId(),
                        idempotencyKey
                );

                return new TransactionResponse(
                        txn.getTransactionReference(),
                        TransactionType.TRANSFER,
                        amount,
                        sourceAccount.getCurrency(),
                        TransactionStatus.COMPLETED,
                        request.getDescription(),
                        sourceAccount.getAccountNumber(),
                        destinationAccount.getAccountNumber(),
                        txn.getCreatedAt()
                );
            });

            auditLogService.logEvent(
                    AuditEventType.TRANSFER_SUCCESS,
                    principal.getUsername(),
                    clientIp,
                    request.getSourceAccountNumber(),
                    ref,
                    "SUCCESS",
                    null,
                    "Transferred " + request.getAmount() + " to " + request.getDestinationAccountNumber()
            );

            idempotencyService.recordSuccess(idempotencyKey, 200, response);
            return response;

        } catch (Exception ex) {
            auditLogService.logEvent(
                    AuditEventType.TRANSFER_FAILED,
                    principal.getUsername(),
                    clientIp,
                    request.getSourceAccountNumber(),
                    ref,
                    "FAILURE",
                    ex.getMessage(),
                    "Transfer failed for amount: " + request.getAmount() + " to " + request.getDestinationAccountNumber()
            );
            idempotencyService.recordFailure(idempotencyKey, 400, ex.getMessage());
            throw ex;
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Transaction amount must be strictly greater than zero");
        }
    }

    private void validateOwnership(Account account, UserPrincipal principal) {
        if (account.getUser() == null || !account.getUser().getId().equals(principal.getId())) {
            throw new UnauthorizedAccountAccessException(
                    "You are not authorized to perform operations on account: " + account.getAccountNumber());
        }
    }

    private void validateAccountActive(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException("Account " + account.getAccountNumber() + " is frozen. Transactions are disabled.");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountFrozenException("Account " + account.getAccountNumber() + " is closed. Transactions are disabled.");
        }
    }

    private Account getOrCreateClearingAccount(String accountNumber, String name) {
        return accountRepository.findByAccountNumber(accountNumber).orElseGet(() -> {
            Account clearing = new Account();
            clearing.setAccountNumber(accountNumber);
            clearing.setAccountName(name);
            clearing.setAccountType(com.ledgerline.enums.AccountType.SYSTEM_CLEARING);
            clearing.setBalance(BigDecimal.ZERO);
            clearing.setCurrency("USD");
            clearing.setStatus(AccountStatus.ACTIVE);
            clearing.setSystemAccount(true);
            return accountRepository.save(clearing);
        });
    }
}
