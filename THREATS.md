# LEDGERLINE – Threat Modeling & Security Architecture Specification
**Document Classification:** Confidential / Production Security Architecture  
**Author:** Naga Sneha – Senior Java Full Stack Developer & Application Security Engineer  
**Challenge:** Snovasys Fresher Hiring Challenge 2026  
**Standards Grounding:** OWASP Top 10, STRIDE Threat Model, ISO/IEC 27001 Financial Controls  

---

## 1. Executive Summary

Financial software demands architectural paranoia. In consumer applications, an eventual consistency delay or race condition creates minor visual glitches; in a double-entry banking system, a race condition causes **double spending, unbacked currency creation, or irrecoverable capital loss**.

`LEDGERLINE` was engineered with a **defense-in-depth security model** where neither the frontend UI, client headers, nor concurrent HTTP threads are ever trusted. Every financial mutation enforces zero-sum conservation laws ($\sum \text{signedAmount} = 0$), pessimistic write row-locking, cryptographic SHA-256 idempotency fingerprinting, server-side authorization validation, and autonomous immutable audit logging.

This document details the complete STRIDE threat analysis, potential attack surfaces, and the specific mitigation mechanisms implemented and tested in the codebase.

---

## 2. STRIDE Threat Analysis Matrix

| Threat ID | Threat Category (STRIDE) | Attack Vector | Severity | LedgerLine Technical Defense | Verification Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **THREAT-01** | **T**ampering / **E**levation | Double Spending via Concurrent Withdrawals | **CRITICAL** | Pessimistic Write Row Locking (`PESSIMISTIC_WRITE`) + Single-Threaded Evaluation | `ConcurrentWithdrawalTest` |
| **THREAT-02** | **D**enial of Service | Mutual Transfer Deadlock Starvation | **HIGH** | Strict Ascending Numerical Account ID Lock Ordering (`id1 < id2`) | `ConcurrentTransferTest` |
| **THREAT-03** | **R**epudiation / **T**ampering | Duplicate Request Submission / Replay | **HIGH** | Cryptographic SHA-256 Idempotency Engine + Unique DB Constraint | `IdempotencyDuplicateTest` |
| **THREAT-04** | **T**ampering | Idempotency Key Reuse with Tampered Payload | **HIGH** | SHA-256 Request Body Hash Comparison; 409 Conflict on Collision | `IdempotencyDuplicateTest` |
| **THREAT-05** | **T**ampering | Simultaneous In-Flight Race on Same Idempotency Key | **HIGH** | Atomic Key Acquisition in Isolated DB Connection (`IN_PROGRESS`) | `IdempotencySimultaneousTest` |
| **THREAT-06** | **T**ampering | Negative Balances & Account Overdraft Exploitation | **CRITICAL** | Java `BigDecimal` balance check + MySQL `CHECK (balance >= 0.0000)` | `NoNegativeBalanceTest` |
| **THREAT-07** | **E**levation of Privilege | Broken Object Level Auth (BOLA / IDOR) | **CRITICAL** | Server-Side Account Ownership Enforcement via Authenticated Principal | `UnauthorizedAccountAccessTest` |
| **THREAT-08** | **E**levation of Privilege | Privilege Escalation during Registration | **HIGH** | Server-enforced `ROLE_ACCOUNT_HOLDER`; role inputs ignored on register | `SecurityConfig` & `AuthService` |
| **THREAT-09** | **T**ampering | Ledger Tampering & Historical Record Modification | **CRITICAL** | Entity immutable fields (`updatable=false`) + No mutation endpoints | `LedgerImmutabilityTest` |
| **THREAT-10** | **E**levation of Privilege | Auditor Unauthorized Mutation | **MEDIUM** | Spring Security `@PreAuthorize` method annotations + Read-only role checks | `AuditorReadOnlyTest` |
| **THREAT-11** | **I**nformation Disclosure | Sensitive Data Leakage & Account Enumeration | **MEDIUM** | Uniform Generic Exceptions (`400 Bad Request` / `404 Not Found`) | `GlobalExceptionHandler` |
| **THREAT-12** | **D**enial of Service | HikariCP Connection Pool Exhaustion | **HIGH** | Decoupled idempotency transactions from row-locked business tx | Concurrency Engine Test Suite |

---

## 3. Deep-Dive Threat Analysis & Architectural Countermeasures

---

### Threat 01: Double Spending & Race Conditions on Withdrawals
* **STRIDE Category:** Tampering / Elevation of Privilege
* **Risk Score:** 9.8 / 10 (Critical)
* **Attack Scenario:**
  An adversary opens an account with an initial balance of \$1,000.00. Using an automated script (e.g., Python `asyncio` or JMeter), the adversary dispatches 10 parallel HTTP requests simultaneously:
  $$\text{POST /api/transactions/withdraw} \quad \text{with amount} = \$1,000.00$$
  If the application executes standard `SELECT balance FROM accounts` followed by `UPDATE accounts SET balance = balance - 1000`, all 10 threads read \$1,000.00 before any thread commits, leading to a \$10,000.00 payout from a \$1,000.00 balance (negative balance of -\$9,000.00).

* **Architectural Defense:**
  1. `AccountRepository.findByIdWithLock(Long id)` uses JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
  2. At the database layer, Hibernate generates:
     ```sql
     SELECT id, account_number, balance, status, ... 
     FROM accounts 
     WHERE id = ? FOR UPDATE;
     ```
  3. The MySQL InnoDB storage engine places an exclusive row-level lock on the target account record.
  4. Only thread 1 executes. Threads 2 through 10 block at the database level until thread 1 commits or rolls back.
  5. When thread 2 acquires the lock, it reads the newly committed balance (\$0.00), fails the `balance.compareTo(amount) < 0` check, throws `InsufficientFundsException`, rolls back, and triggers an audit log entry.
  6. In addition, MySQL database-level schema constraints enforce:
     ```sql
     CONSTRAINT chk_accounts_balance_positive CHECK (balance >= 0.0000)
     ```
     Any hypothetical bypass of the application layer causes an immediate InnoDB SQL execution abort.

* **Automated Test Validation:**
  Verified in `ConcurrentWithdrawalTest.java` with 10 concurrent executor threads competing for a single \$1,000.00 account balance. Exactly 1 transaction succeeds; 9 transactions fail with `InsufficientFundsException`. Final balance is verified at exactly \$0.00.

---

### Threat 02: Deadlocks in Concurrent Mutual Transfers
* **STRIDE Category:** Denial of Service
* **Risk Score:** 8.5 / 10 (High)
* **Attack Scenario:**
  Two account holders or algorithmic trading bots transfer funds to each other at the exact same millisecond:
  - Thread 1: Transmits \$100.00 from Account 101 to Account 202.
  - Thread 2: Transmits \$100.00 from Account 202 to Account 101.
  If Thread 1 locks Account 101 and requests Account 202, while Thread 2 locks Account 202 and requests Account 101, a cyclic dependency forms. MySQL InnoDB detects the deadlock and aborts one or both transactions with `Deadlock found when trying to get lock; try restarting transaction`.

* **Architectural Defense:**
  In `TransactionService.java`, before any lock is requested, the system inspects the primary key IDs of the source and destination accounts and sorts them numerically:
  ```java
  Long firstLockId = sourceAccount.getId() < destAccount.getId() 
          ? sourceAccount.getId() : destAccount.getId();
  Long secondLockId = sourceAccount.getId() < destAccount.getId() 
          ? destAccount.getId() : sourceAccount.getId();

  // Always acquire row locks in strictly ascending numerical order:
  accountRepository.findByIdWithLock(firstLockId);
  accountRepository.findByIdWithLock(secondLockId);
  ```
  Because all threads across the entire JVM always acquire locks in the identical order ($101 \rightarrow 202$), cyclic lock dependency is mathematically impossible. Deadlocks are completely eliminated without retry loops.

* **Automated Test Validation:**
  Verified in `ConcurrentTransferTest.java` with 8 concurrent threads executing mutual bidirectional transfers ($A \rightarrow B$ and $B \rightarrow A$) simultaneously across 10 rounds (80 total transactions). Zero deadlocks occur, and all 80 transfers succeed with 100% money conservation.

---

### Threat 03: Replay Attacks & Duplicate Requests (Network Latency)
* **STRIDE Category:** Repudiation / Tampering
* **Risk Score:** 8.2 / 10 (High)
* **Attack Scenario:**
  A mobile client or browser user clicks "Confirm Wire Transfer" for \$5,000.00. The server processes the transaction, but a cellular network hiccup drops the HTTP 200 OK response packet. The mobile app automatically retries the POST request, or the impatient user clicks the button again. Without idempotency, \$10,000.00 is debited.

* **Architectural Defense:**
  1. The client generates a unique UUID v4 and sends it in the `Idempotency-Key` HTTP header.
  2. The server computes a SHA-256 cryptographic digest of the HTTP request payload:
     $$\text{Request Hash} = \text{SHA-256}(\text{Source} + \text{Dest} + \text{Amount} + \text{Desc})$$
  3. The `idempotency_keys` table has a database `UNIQUE KEY (idempotency_key)`.
  4. On retry with the same key and identical payload:
     - The server locates the existing committed record.
     - The business logic is skipped entirely (no balances altered, no ledger entries created).
     - The cached JSON response and original HTTP status code are returned immediately to the client.

* **Automated Test Validation:**
  Verified in `IdempotencyDuplicateTest.java`. The second identical request receives HTTP 200 with the original transaction reference, and the account balance is debited only once.

---

### Threat 04: Idempotency Key Reuse with Tampered Payload (Collision Attack)
* **STRIDE Category:** Tampering
* **Risk Score:** 8.0 / 10 (High)
* **Attack Scenario:**
  An attacker intercepts a legitimate request header containing `Idempotency-Key: c9b2f4...` for a \$10.00 deposit, and later attempts to send a new request with the same key but requesting a \$50,000.00 transfer or altering the destination account.

* **Architectural Defense:**
  When `IdempotencyService.checkOrLock()` encounters an existing key:
  ```java
  if (!record.getRequestHash().equals(currentRequestHash)) {
      throw new IdempotencyConflictException(
          "Idempotency key '" + idempotencyKey + "' has already been used with a different request payload."
      );
  }
  ```
  The server compares the current SHA-256 hash with the stored hash. If they do not match byte-for-byte, an `IdempotencyConflictException` is thrown, resulting in an immediate `HTTP 409 Conflict`. No execution occurs.

* **Automated Test Validation:**
  Verified in `IdempotencyDuplicateTest.testDuplicateKeyWithDifferentPayloadIsRejected()`.

---

### Threat 05: Simultaneous In-Flight Race on Same Idempotency Key
* **STRIDE Category:** Tampering / Race Condition
* **Risk Score:** 8.0 / 10 (High)
* **Attack Scenario:**
  An adversary sends two identical requests with the same idempotency key at the exact same sub-millisecond, before the first request has completed and saved its final response.

* **Architectural Defense:**
  1. `IdempotencyService.checkOrLock()` runs in its own isolated database transaction (`Propagation.REQUIRES_NEW`).
  2. It immediately inserts a row with status `IN_PROGRESS`:
     ```sql
     INSERT INTO idempotency_keys (idempotency_key, request_hash, status, created_at)
     VALUES (?, ?, 'IN_PROGRESS', NOW());
     ```
  3. Because `idempotency_key` is unique, the second simultaneous thread triggers a MySQL duplicate key exception:
     `DataIntegrityViolationException: Duplicate entry '...' for key 'idempotency_keys.uk_idempotency_key'`
  4. The exception handler translates this into:
     ```java
     throw new ConcurrentRequestException("A request with idempotency key '...' is currently being processed.");
     ```
     Returning `HTTP 409 Conflict` to the caller.

* **Automated Test Validation:**
  Verified in `IdempotencySimultaneousTest.java` using two threads dispatched simultaneously via `CountDownLatch`. Exactly one acquires the lock; the other receives HTTP 409.

---

### Threat 06: Negative Account Balances & Floating Point Precision Loss
* **STRIDE Category:** Tampering
* **Risk Score:** 9.5 / 10 (Critical)
* **Attack Scenario:**
  1. **Floating Point Rounding:** Using Java `double` or `float` introduces binary floating point representation inaccuracies (e.g., $0.1 + 0.2 = 0.30000000000000004$). In high-volume financial accounting, accumulated fractions create balance discrepancies and reconciliation failures.
  2. **Negative Overdraft Exploitation:** Users submitting negative amounts (e.g., `amount = -500.00` in a withdrawal) to turn a debit into an unauthorized credit.

* **Architectural Defense:**
  1. All monetary quantities in Java are strictly typed as `java.math.BigDecimal`.
  2. In MySQL, all balance and amount columns use `DECIMAL(19, 4)`:
     ```sql
     balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000
     amount DECIMAL(19, 4) NOT NULL
     ```
  3. All API DTO inputs are validated with Bean Validation `@DecimalMin(value = "0.01", inclusive = true)` and `@NotNull`.
  4. `TransactionService` explicitly verifies:
     ```java
     if (amount.compareTo(BigDecimal.ZERO) <= 0) {
         throw new InvalidTransactionAmountException("Transaction amount must be strictly positive");
     }
     ```
  5. Prior to any debit, available balance is evaluated:
     ```java
     if (sourceAccount.getBalance().compareTo(amount) < 0) {
         throw new InsufficientFundsException("Insufficient funds in account: " + accountNumber);
     }
     ```
  6. The database schema enforces a strict check constraint:
     ```sql
     CONSTRAINT chk_accounts_balance_positive CHECK (balance >= 0.0000)
     ```

* **Automated Test Validation:**
  Verified in `NoNegativeBalanceTest.java` and `MoneyConservationTest.java`. Negative amounts and overdrafts are rejected with zero balance change.

---

### Threat 07: Broken Object Level Authorization (BOLA / IDOR)
* **STRIDE Category:** Elevation of Privilege
* **Risk Score:** 9.0 / 10 (Critical)
* **Attack Scenario:**
  User Bob (`id=2`) initiates a withdrawal or transfer. In the HTTP request payload, Bob supplies Alice's account number (`LL-1001-ALICE`). If the server only checks that Bob is authenticated and that the account exists, Bob steals Alice's money.

* **Architectural Defense:**
  In `TransactionService.java`, before processing any withdrawal or source debit:
  ```java
  private void validateAccountOwnership(Account account, UserPrincipal principal) {
      if (principal.getRole() == Role.ROLE_ADMIN) {
          return; // Explicit administrative oversight
      }
      if (account.isSystemAccount()) {
          throw new UnauthorizedAccountAccessException("Direct access to system clearing accounts is forbidden");
      }
      if (!account.getUser().getId().equals(principal.getId())) {
          throw new UnauthorizedAccountAccessException(
              "Access denied: Account " + account.getAccountNumber() + " does not belong to the authenticated user"
          );
      }
  }
  ```
  The account's foreign key `user_id` is validated against `SecurityContextHolder.getContext().getAuthentication().getPrincipal().getId()`. Any mismatch throws `UnauthorizedAccountAccessException` and records a refused attempt in the audit table.

* **Automated Test Validation:**
  Verified in `UnauthorizedAccountAccessTest.java` and `UnauthorizedTransferTest.java`. Bob attempting to withdraw or transfer from Alice's account receives `HTTP 403 Forbidden`.

---

### Threat 08: Privilege Escalation During User Registration
* **STRIDE Category:** Elevation of Privilege
* **Risk Score:** 8.8 / 10 (High)
* **Attack Scenario:**
  A malicious client registers via `POST /api/auth/register`, injecting `"role": "ROLE_ADMIN"` into the JSON payload.

* **Architectural Defense:**
  1. `RegisterRequest.java` contains only: `username`, `email`, `password`, `fullName`. It does not contain any `role` field.
  2. In `AuthService.java`, the role is hardcoded on user instantiation:
     ```java
     User user = new User(
         request.getUsername(),
         request.getEmail(),
         passwordEncoder.encode(request.getPassword()),
         request.getFullName(),
         Role.ROLE_ACCOUNT_HOLDER // Strictly hardcoded server-side
     );
     ```
  3. Administrator and Compliance Auditor accounts can only be provisioned by authorized DBAs via `database/seed.sql`.

* **Automated Test Validation:**
  Verified in `SecurityConfig.java` tests and role endpoint authorization suites.

---

### Threat 09: Ledger Tampering & Historical Record Modification
* **STRIDE Category:** Tampering / Non-Repudiation
* **Risk Score:** 9.5 / 10 (Critical)
* **Attack Scenario:**
  A rogue insider or attacker modifies a row in `ledger_entries` or `transactions` to conceal an unauthorized withdrawal or alter a transaction amount.

* **Architectural Defense:**
  1. JPA Entity definition on `LedgerEntry.java`:
     ```java
     @Column(name = "amount", nullable = false, updatable = false)
     private BigDecimal amount;

     @Column(name = "entry_type", nullable = false, updatable = false)
     private EntryType entryType;

     @Column(name = "balance_after", nullable = false, updatable = false)
     private BigDecimal balanceAfter;
     ```
  2. No repository methods exist for `update` or `delete` on `LedgerTransactionRepository` or `LedgerEntryRepository`.
  3. No REST endpoints exist in the application for updating or deleting transactions or entries.
  4. In `DoubleEntryLedgerService`, entries can only be appended to the journal.

* **Automated Test Validation:**
  Verified in `LedgerImmutabilityTest.java`. Direct JPA update attempts fail or throw `DataIntegrityViolationException`.

---

### Threat 10: Auditor Unauthorized Mutation
* **STRIDE Category:** Elevation of Privilege
* **Risk Score:** 7.5 / 10 (High)
* **Attack Scenario:**
  A compliance auditor with `ROLE_AUDITOR` attempts to initiate a transfer or create an account.

* **Architectural Defense:**
  Method-level Spring Security annotations enforce strict role boundaries:
  ```java
  // In TransactionController.java:
  @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
  public ResponseEntity<?> transfer(...)

  // In AccountController.java:
  @PreAuthorize("hasAnyRole('ACCOUNT_HOLDER', 'ADMIN')")
  public ResponseEntity<?> createAccount(...)
  ```
  Auditors possess read-only authority. Any attempt to call mutation endpoints returns `HTTP 403 Forbidden`.

* **Automated Test Validation:**
  Verified in `AuditorReadOnlyTest.java`. The auditor successfully reads transaction history and audit logs, but is rejected with `HTTP 403` on deposit, withdrawal, and transfer requests.

---

### Threat 11: Information Disclosure & Account Enumeration
* **STRIDE Category:** Information Disclosure
* **Risk Score:** 6.5 / 10 (Medium)
* **Attack Scenario:**
  An adversary sends probes to `/api/auth/login` to deduce valid usernames based on different error messages ("User not found" vs "Wrong password").

* **Architectural Defense:**
  1. `AuthService.java` throws a unified message on authentication failure:
     ```java
     throw new BadCredentialsException("Invalid username or password");
     ```
  2. `GlobalExceptionHandler.java` catches all authentication exceptions and returns uniform JSON:
     ```json
     {
       "success": false,
       "message": "Invalid username or password",
       "data": null
     }
     ```
  3. Stack traces and internal database error messages are suppressed in production mode.

---

### Threat 12: HikariCP Connection Pool Exhaustion & DoS
* **STRIDE Category:** Denial of Service
* **Risk Score:** 8.5 / 10 (High)
* **Discovery & Architectural Resolution:**
  During early concurrency testing of 10 simultaneous threads, an architectural hazard was uncovered:
  - If an outer transaction holds a database connection from HikariCP while waiting for an inner `@Transactional(propagation = Propagation.REQUIRES_NEW)` method (such as `IdempotencyService` or `AuditLogService`), the thread demands **two simultaneous connections** from the pool.
  - When all available pool connections are acquired by outer transactions, all threads deadlock waiting for the inner connection, freezing the application.

* **Architectural Defense:**
  `TransactionService` was refactored to use Spring's programmatic `TransactionTemplate`:
  1. `IdempotencyService.checkOrLock()` executes and commits in its own connection **first**, then releases the connection back to HikariCP.
  2. `TransactionTemplate.execute()` runs the business transaction, row locks, and ledger journal entry, then releases its connection.
  3. `IdempotencyService.recordSuccess()` acquires a connection, commits the cached response, and releases it.
  4. No thread ever holds more than **one** connection at any instant. Hikari pool starvation is completely eliminated.

---

## 4. Security Verification Summary

| Verification Suite | Target Security Control | Result |
| :--- | :--- | :--- |
| `MoneyConservationTest` | Zero-sum double entry invariant ($\sum = 0$) | **PASSED (100%)** |
| `NoNegativeBalanceTest` | Zero overdraft & check constraint enforcement | **PASSED (100%)** |
| `ConcurrentWithdrawalTest` | Pessimistic row locking against race conditions | **PASSED (100%)** |
| `ConcurrentTransferTest` | Deadlock-free ascending ID lock ordering | **PASSED (100%)** |
| `IdempotencyDuplicateTest` | SHA-256 fingerprinting & duplicate return | **PASSED (100%)** |
| `IdempotencySimultaneousTest` | In-flight collision detection (HTTP 409) | **PASSED (100%)** |
| `UnauthorizedAccountAccessTest`| Server-side account ownership verification | **PASSED (100%)** |
| `UnauthorizedTransferTest` | BOLA / IDOR prevention across accounts | **PASSED (100%)** |
| `AuditorReadOnlyTest` | Read-only auditor authorization enforcement | **PASSED (100%)** |
| `LedgerImmutabilityTest` | Write-once append-only ledger integrity | **PASSED (100%)** |

All tests execute cleanly with **0 failures and 0 errors** via `./mvnw test`.
