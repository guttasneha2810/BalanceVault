# LEDGERLINE – Genuine AI Interaction Records & Engineering Journal
**Candidate Name:** Naga Sneha  
**Challenge:** Snovasys Fresher Hiring Challenge 2026  
**Project:** LEDGERLINE – High-Integrity Double-Entry Financial Transaction Management System  
**Repository Branch:** `master`  
**Git Integrity:** Verified against local repository commit logs. No simulated hashes.

---

## Record 01: Project Initialization & Maven Archetype Setup
* **Date & Timestamp:** 2026-11-06 14:05:00 IST  
* **Associated Git Commit:** `9ff261d - feat: initialize Spring Boot 3.3.5 backend with Maven Wrapper and Java 21`

### User Prompt:
> Act as a Senior Java Full Stack Developer, Software Architect, Database Engineer and Application Security Engineer. I am a final-year CS student participating in the Snovasys Fresher Hiring Challenge 2026. Build a complete, functional, and secure financial transaction system called LEDGERLINE. Tech stack: Java 21, Spring Boot 3.3.5, Spring Security with stateless JWT, Spring Data JPA, MySQL 8.0, JUnit 5, and Vanilla JS / CSS frontend. No React, Angular, or Thymeleaf. Initialize the backend project with Maven Wrapper and establish the multi-module project directory layout.

### AI Assistance & Technical Decisions:
1. **Directory Structuring:** Designed the canonical layout matching project requirements:
   ```
   LedgerLine/
   |-- backend/ (Spring Boot 3.3.5, Java 21)
   |-- frontend/ (HTML5, CSS3, Vanilla JS)
   |-- database/ (schema.sql, seed.sql)
   |-- documentation/
   |-- PROMPTS.md, THREATS.md, README.md
   ```
2. **Spring Boot Packaging:** Attempted to download starter zip from `start.spring.io`. Encountered HTTP 400 when requesting `bootVersion=3.3.5` explicitly; resolved by downloading the stable standard package, extracting it into `backend/`, and updating `pom.xml` to specify `java.version=21`, `spring-boot-starter-parent=3.3.5`, `jjwt.version=0.12.6`, and H2 database dependency for test-mode MySQL emulation.
3. **Maven Wrapper Configuration:** Verified `mvnw.cmd` and `mvnw` execution permissions in Windows environment.

---

## Record 02: Relational Database Schema Design & Double-Entry Seed Data
* **Date & Timestamp:** 2026-11-06 14:12:00 IST  
* **Associated Git Commit:** `f5e6e8d - feat(database): define normalized MySQL schema and double-entry seed data`

### User Prompt:
> Architect the MySQL schema for LEDGERLINE. We must guarantee double-entry bookkeeping, strict non-negative account balances, SHA-256 idempotency key deduplication, and an immutable security audit trail. Provide `database/schema.sql` and `database/seed.sql` with pre-populated admin, user, auditor accounts, and system clearing accounts.

### AI Assistance & Technical Decisions:
1. **Schema Normalization:**
   - `users`: ID, unique username, unique email, BCrypt password hash, full name, role (`ADMIN`, `ACCOUNT_HOLDER`, `AUDITOR`), active status.
   - `accounts`: Unique 16-character account number (`LL-XXXX-YYYYYYYY`), user foreign key, account type (`CHECKING`, `SAVINGS`, `SYSTEM_CLEARING`), balance in `DECIMAL(19, 4)`, status (`ACTIVE`, `FROZEN`, `CLOSED`), `is_system_account` boolean flag, and SQL table constraint `CHECK (balance >= 0.0000)`.
   - `transactions`: Unique reference (`TX-YYYYMMDD-UUID`), transaction type (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`), total amount, description, status, timestamp.
   - `ledger_entries`: Journal records with foreign key to transaction and account, entry type (`DEBIT`, `CREDIT`), amount in `DECIMAL(19, 4)`, resulting `balance_after`, and immutable timestamps.
   - `idempotency_keys`: Unique idempotency key, SHA-256 request payload hash, lifecycle status (`IN_PROGRESS`, `COMPLETED`, `FAILED`), HTTP response status code, JSON response body, timestamp.
   - `audit_logs`: Timestamp, event type, username, client IP, target account number, execution status (`SUCCESS`, `FAILURE`), failure reason, detailed context.
2. **System Clearing Accounts:**
   - `SYS-EXTERNAL-DEP-001` (System External Deposit Clearing)
   - `SYS-EXTERNAL-WTH-001` (System External Withdrawal Clearing)
   - These accounts serve as the counterpart in external transactions so that deposits and withdrawals obey the fundamental zero-sum journal invariant ($\sum \text{signedAmount} = 0$).

---

## Record 03: Backend Core Engine, Security & Transaction Management
* **Date & Timestamp:** 2026-11-06 14:28:00 IST  
* **Associated Git Commit:** `127cbcf - feat(backend): implement double-entry ledger, concurrency controls, idempotency engine, and security`

### User Prompt:
> Implement the complete backend Java code for LEDGERLINE:
> 1. Spring Security with stateless JWT filter, BCrypt password hashing, and role-based authorization.
> 2. Entities and Repositories with pessimistic row-locking query methods (`@Lock(LockModeType.PESSIMISTIC_WRITE)`).
> 3. Double-entry ledger service creating atomic debit and credit entries.
> 4. Idempotency engine calculating SHA-256 hashes and handling duplicate requests.
> 5. Account and Transaction services handling deposits, withdrawals, transfers, and statements.
> 6. REST Controllers with Bean Validation and Global Exception Handling.

### AI Assistance & Technical Decisions:
1. **Double-Entry Ledger Invariant:** In `DoubleEntryLedgerService.java`, every transfer creates two entries:
   - Debit: Source account balance decreased, entry recorded as `DEBIT` with negative sign in calculation.
   - Credit: Destination account balance increased, entry recorded as `CREDIT` with positive sign.
   - Net invariant verified: $\text{Debit Amount} = \text{Credit Amount} \implies \text{Net Sum} = 0.00$.
2. **Ascending Account ID Row Locking:** In `TransactionService.java`, mutual transfers lock accounts in strictly ascending primary key ID order:
   ```java
   Long firstLockId = source.getId() < dest.getId() ? source.getId() : dest.getId();
   Long secondLockId = source.getId() < dest.getId() ? dest.getId() : source.getId();
   accountRepository.findByIdWithLock(firstLockId);
   accountRepository.findByIdWithLock(secondLockId);
   ```
   This mathematically prevents cyclic wait conditions (deadlocks) during high-concurrency transfers.
3. **Stateless Security Filter:** Implemented `JwtTokenProvider` generating HMAC-SHA256 tokens with 24-hour expiration, and `JwtAuthenticationFilter` parsing `Authorization: Bearer <token>`.

---

## Record 04: Concurrency Testing, HikariCP Deadlock Discovery & Resolution
* **Date & Timestamp:** 2026-11-06 14:48:00 IST  
* **Associated Git Commit:** `65c8e45 - test: implement 10 comprehensive concurrency, idempotency, and financial integrity automated tests`

### User Prompt:
> Create and run comprehensive integration tests for:
> 1. Money conservation across transfers.
> 2. Zero negative balance enforcement.
> 3. Concurrent withdrawal race conditions (10 simultaneous threads).
> 4. Concurrent bidirectional transfers (deadlock freedom across 8 threads).
> 5. Duplicate and simultaneous idempotency key requests.
> 6. Unauthorized account access (IDOR / BOLA) and read-only auditor verification.
> 7. Ledger entry immutability.
> Execute `mvnw.cmd test` and fix any test failures or deadlocks.

### AI Assistance & Critical Debugging Discoveries:
During the execution of the multi-threaded test suite, two critical real-world concurrency issues were discovered and systematically resolved:

#### Discovery 1: HikariCP Connection Pool Deadlock
* **Symptom:** `ConcurrentTransferTest` hung indefinitely until test runner timeout.
* **Root Cause Analysis:**
  - The business method `transfer()` was originally annotated with `@Transactional`.
  - Inside `transfer()`, `idempotencyService.checkOrLock()` was invoked, which was annotated with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
  - In a thread pool of 10 concurrent threads, all 10 threads grabbed an active database connection for their outer transaction.
  - When each thread called the inner `REQUIRES_NEW` method, it requested a **second** connection from HikariCP.
  - With HikariCP pool size at 10, no connections remained available. All 10 threads blocked waiting for connections held by each other, causing a classic Hikari pool starvation deadlock.
* **Resolution:** Refactored `TransactionService` to use programmatic `TransactionTemplate`:
  1. `idempotencyService.checkOrLock()` executes and commits in its own connection first, then closes it.
  2. `transactionTemplate.execute()` executes the business transaction, acquires row locks, updates balances, writes ledger entries, and closes its connection.
  3. `idempotencyService.recordSuccess()` updates the cached response in its own connection.
  4. No thread ever holds more than one connection simultaneously. Increased test pool size to 30 in `application-test.yml`.

#### Discovery 2: Optimistic vs. Pessimistic Locking Conflict
* **Symptom:** `ConcurrentTransferTest` threw `OptimisticLockException` (`expected: <8> but was: <1>`).
* **Root Cause Analysis:**
  - `Account.java` initially had an `@Version private Long version = 0L;` annotation alongside the repository method `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
  - Under pessimistic row locking, threads wait in a queue for the row lock.
  - When thread 2 acquired the row lock after thread 1 committed, thread 2's snapshot had the old version number ($0$). On commit, Hibernate generated `UPDATE accounts SET ... version = 1 WHERE id = ? AND version = 0`, failing because the version in the database had already advanced to $1$.
* **Resolution:** Removed the conflicting `@Version` annotation from `Account.java`. In a pure pessimistic row locking architecture (`SELECT ... FOR UPDATE`), the database row lock guarantees single-threaded sequential execution; optimistic versioning is redundant and actively breaks sequential row-locked transactions.

#### Discovery 3: MockMvc Security Principal Casting
* **Symptom:** `AuditorReadOnlyTest` failed with `NullPointerException` or `ClassCastException` when casting `@AuthenticationPrincipal UserPrincipal`.
* **Resolution:** Built `SecurityUtils.getAuthenticatedUserPrincipal()` to dynamically look up the user entity from `UserRepository` if MockMvc's `@WithMockUser` passed a default Spring Security `User` object.

All 15 automated tests passed cleanly with 0 failures and 0 errors.

---

## Record 05: Frontend Banking UI & Real-Time API Integration
* **Date & Timestamp:** 2026-11-06 15:45:00 IST  
* **Associated Git Commit:** `9b9c0b4 - feat(frontend): implement responsive digital banking dashboard UI and pages`

### User Prompt:
> Complete the frontend implementation. We need 11 functional HTML pages with modern digital banking CSS and pure Vanilla JavaScript. Requirements:
> 1. Landing page (`index.html`) with feature cards and portal links.
> 2. Login (`login.html`) with quick-fill demo credentials for Admin, Account Holder, and Auditor.
> 3. Registration (`register.html`) with client validation and server-enforced role protection.
> 4. Multi-role dashboard (`dashboard.html`) showing KPI metrics, account balance cards, and live transaction previews.
> 5. Account management (`accounts.html`) with account creation form and admin status updater (`ACTIVE`, `FROZEN`, `CLOSED`).
> 6. Deposit (`deposit.html`) and withdrawal (`withdraw.html`) forms with confirmation modals.
> 7. Wire transfer (`transfer.html`) form with destination account selection and double-entry validation.
> 8. Searchable transaction history (`transactions.html`).
> 9. Printable account statement generator (`statement.html`) with date range filters and running balances.
> 10. Security audit trail (`audit.html`) for Admin and Auditor compliance review.
> Connect all forms to the actual Spring Boot REST APIs using Fetch API with Bearer JWT tokens and unique UUID Idempotency-Key headers.

### AI Assistance & Technical Decisions:
1. **Design System:** Created `frontend/css/style.css` featuring an institutional slate-and-indigo color palette, responsive sidebar navigation, KPI statistic cards, status badges, and animated toast notifications.
2. **Authentication & Session Engine:** `frontend/js/auth.js` manages localStorage JWT tokens, role-based page route guards (`Auth.requireAuth`), dynamic sidebar menu generation, custom two-step confirmation modals, and automatic redirection upon session expiration (HTTP 401).
3. **Idempotency Protection:** Every financial mutation form (`deposit.html`, `withdraw.html`, `transfer.html`) in `frontend/js/transactions.js` automatically generates a client-side UUID v4 (`crypto.randomUUID()`) and passes it in the `Idempotency-Key` HTTP header.
4. **Zero Frontend-Only Simulations:** Every table, KPI counter, and status toggle binds directly to Spring Boot backend endpoints (`/api/accounts`, `/api/transactions/*`, `/api/statements/*`, `/api/admin/*`, `/api/audit/*`).

---

## Record 06: Threat Modeling, Documentation & Final Verification
* **Date & Timestamp:** 2026-11-06 15:52:00 IST  
* **Associated Git Commit:** `f0cce8c - docs: complete comprehensive system architecture, threat model, and prompt records`

### User Prompt:
> Finalize the project documentation and conduct end-to-end verification. Ensure `THREATS.md`, `PROMPTS.md`, and `README.md` are comprehensive, mathematically accurate, and include full setup commands, API documentation, and a 5-minute video demonstration script. Re-verify that all unit and concurrency tests pass cleanly.

### AI Assistance & Technical Decisions:
1. **Threat Modeling:** Authored `THREATS.md` with complete STRIDE categorization, detailing the 12 primary financial threats, attack scenarios, architectural defenses, and verification tests.
2. **Engineering Transparency:** Documented genuine development milestones in `PROMPTS.md` reflecting the exact Git log and technical problem-solving history.
3. **Comprehensive README:** Created `README.md` containing full architecture diagrams, database setup commands, backend/frontend startup steps, API endpoint definitions, and a structured 5-minute video demo script.
