package com.ledgerline.service;

import com.ledgerline.entity.Account;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerTransaction;
import com.ledgerline.enums.EntryType;
import com.ledgerline.enums.TransactionStatus;
import com.ledgerline.enums.TransactionType;
import com.ledgerline.exception.InvalidTransactionException;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.LedgerTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class DoubleEntryLedgerService {

    private static final Logger log = LoggerFactory.getLogger(DoubleEntryLedgerService.class);

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    public DoubleEntryLedgerService(LedgerTransactionRepository transactionRepository, LedgerEntryRepository entryRepository) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordInternalTransfer(
            String reference,
            Account sourceAccount,
            Account destinationAccount,
            BigDecimal amount,
            String currency,
            String description,
            Long initiatedByUserId,
            String idempotencyKey) {

        BigDecimal scaledAmount = amount.setScale(4, RoundingMode.HALF_UP);

        // 1. Create parent transaction record
        LedgerTransaction txn = new LedgerTransaction(
                reference,
                TransactionType.TRANSFER,
                scaledAmount,
                currency,
                description,
                initiatedByUserId,
                idempotencyKey
        );
        txn = transactionRepository.save(txn);

        // 2. Post Debit on Source Account (Signed amount: -Amount)
        LedgerEntry debitEntry = new LedgerEntry(
                txn,
                sourceAccount,
                EntryType.DEBIT,
                scaledAmount,
                scaledAmount.negate(),
                sourceAccount.getBalance().setScale(4, RoundingMode.HALF_UP)
        );
        entryRepository.save(debitEntry);

        // 3. Post Credit on Destination Account (Signed amount: +Amount)
        LedgerEntry creditEntry = new LedgerEntry(
                txn,
                destinationAccount,
                EntryType.CREDIT,
                scaledAmount,
                scaledAmount,
                destinationAccount.getBalance().setScale(4, RoundingMode.HALF_UP)
        );
        entryRepository.save(creditEntry);

        // 4. Verify Double-Entry Zero-Sum Invariant
        verifyDoubleEntryInvariant(txn.getId());

        log.info("Double-entry internal transfer recorded: ref={}, DR={}, CR={}, amount={}",
                reference, sourceAccount.getAccountNumber(), destinationAccount.getAccountNumber(), scaledAmount);

        return txn;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordExternalDeposit(
            String reference,
            Account clearingAccount,
            Account destinationAccount,
            BigDecimal amount,
            String currency,
            String description,
            Long initiatedByUserId,
            String idempotencyKey) {

        BigDecimal scaledAmount = amount.setScale(4, RoundingMode.HALF_UP);

        LedgerTransaction txn = new LedgerTransaction(
                reference,
                TransactionType.DEPOSIT,
                scaledAmount,
                currency,
                description,
                initiatedByUserId,
                idempotencyKey
        );
        txn = transactionRepository.save(txn);

        // Debit External Clearing Account (Source of funds entering bank)
        LedgerEntry debitClearing = new LedgerEntry(
                txn,
                clearingAccount,
                EntryType.DEBIT,
                scaledAmount,
                scaledAmount.negate(),
                clearingAccount.getBalance().setScale(4, RoundingMode.HALF_UP)
        );
        entryRepository.save(debitClearing);

        // Credit User Account (Funds received by user)
        LedgerEntry creditUser = new LedgerEntry(
                txn,
                destinationAccount,
                EntryType.CREDIT,
                scaledAmount,
                scaledAmount,
                destinationAccount.getBalance().setScale(4, RoundingMode.HALF_UP)
        );
        entryRepository.save(creditUser);

        verifyDoubleEntryInvariant(txn.getId());

        log.info("Double-entry deposit recorded: ref={}, clearing={}, user={}, amount={}",
                reference, clearingAccount.getAccountNumber(), destinationAccount.getAccountNumber(), scaledAmount);

        return txn;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction recordExternalWithdrawal(
            String reference,
            Account sourceAccount,
            Account clearingAccount,
            BigDecimal amount,
            String currency,
            String description,
            Long initiatedByUserId,
            String idempotencyKey) {

        BigDecimal scaledAmount = amount.setScale(4, RoundingMode.HALF_UP);

        LedgerTransaction txn = new LedgerTransaction(
                reference,
                TransactionType.WITHDRAWAL,
                scaledAmount,
                currency,
                description,
                initiatedByUserId,
                idempotencyKey
        );
        txn = transactionRepository.save(txn);

        // Debit User Account (Funds leaving user)
        LedgerEntry debitUser = new LedgerEntry(
                txn,
                sourceAccount,
                EntryType.DEBIT,
                scaledAmount,
                scaledAmount.negate(),
                sourceAccount.getBalance().setScale(4, RoundingMode.HALF_UP)
        );
        entryRepository.save(debitUser);

        // Credit External Clearing Account (Funds delivered to external clearing)
        LedgerEntry creditClearing = new LedgerEntry(
                txn,
                clearingAccount,
                EntryType.CREDIT,
                scaledAmount,
                scaledAmount,
                clearingAccount.getBalance().setScale(4, RoundingMode.HALF_UP)
        );
        entryRepository.save(creditClearing);

        verifyDoubleEntryInvariant(txn.getId());

        log.info("Double-entry withdrawal recorded: ref={}, user={}, clearing={}, amount={}",
                reference, sourceAccount.getAccountNumber(), clearingAccount.getAccountNumber(), scaledAmount);

        return txn;
    }

    private void verifyDoubleEntryInvariant(Long transactionId) {
        BigDecimal sum = entryRepository.sumSignedAmountByTransactionId(transactionId);
        if (sum == null || sum.compareTo(BigDecimal.ZERO) != 0) {
            String errorMsg = String.format("CRITICAL: Double-entry accounting invariant violated for transaction %d! Sum of entries: %s",
                    transactionId, sum);
            log.error(errorMsg);
            throw new InvalidTransactionException(errorMsg);
        }
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForAccount(Long accountId) {
        return entryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForTransaction(Long transactionId) {
        return entryRepository.findByLedgerTransactionId(transactionId);
    }
}
