# BalanceVault – High-Integrity Financial Transaction Management System

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Tests](https://img.shields.io/badge/Tests-15%20Passed%20(100%25)-success)](backend/src/test/)
[![Security](https://img.shields.io/badge/Security-STRIDE%20Verified-blueviolet)](THREATS.md)

> **Snovasys Fresher Hiring Challenge 2026 – Individual Assessment**  
> **Candidate:** Naga Sneha  
> **Role Grounding:** Senior Java Full Stack Developer, Software Architect, Database Engineer & Application Security Engineer  

---

## 1. Executive Summary & Problem Statement

Modern enterprise financial systems cannot tolerate eventual consistency or concurrency oversights. When multiple operations execute simultaneously against the same monetary account, naive implementations suffer from **race conditions, deadlocks, double spending, negative overdrafts, and phantom money creation**.

**BalanceVault** is an enterprise-grade financial transaction engine engineered to uphold mathematical conservation laws under extreme concurrency. Built strictly with **Java 21, Spring Boot 3.3.5, Spring Security (stateless JWT), Spring Data JPA / Hibernate, PostgreSQL, and pure Vanilla JavaScript**, BalanceVault guarantees:

1. **Zero-Sum Double-Entry Accounting:** Every financial transfer creates balanced debit and credit entries ($\sum \text{signedAmount} = 0$). External deposits and withdrawals balance via designated system clearing accounts.
2. **Deadlock-Free Pessimistic Row Locking:** Simultaneous mutual transfers acquire database write locks (`SELECT ... FOR UPDATE`) in strictly ascending numerical account ID order, mathematically eliminating cyclic deadlocks.
3. **Cryptographic Idempotency Engine:** HTTP requests carrying an `Idempotency-Key` are fingerprinted with SHA-256 hashes. Safe identical retries receive the cached original response without re-executing balance mutations; tampered payloads on reused keys are rejected with `HTTP 409 Conflict`.
4. **Absolute Balance Integrity:** All monetary operations utilize `java.math.BigDecimal` and PostgreSQL `DECIMAL(19, 4)` with database-level `CHECK (balance >= 0.0000)` constraints to eliminate overdrafts and floating-point rounding errors.
5. **Strict Three-Tier Role-Based Security:** Server-enforced authorization across `ADMIN`, `ACCOUNT_HOLDER`, and read-only `AUDITOR` roles with privilege escalation prevention.
6. **Autonomous Audit Trail:** Isolated audit logging (`REQUIRES_NEW`) records both successful transactions and refused attempts with forensic context.

---

## 2. System Architecture

```
+-----------------------------------------------------------------------------------+
|                            CLIENT TIER (HTML5 / CSS3 / Vanilla JS)                |
|  - auth.js: JWT session handling, Bearer injection, role-based UI route guards     |
|  - transactions.js: UUID v4 Idempotency-Key generation, confirmation modals       |
|  - dashboard.js / accounts.js: Live balance feeds & real-time REST API consumption|
+-----------------------------------------------------------------------------------+
                                         |
                                         | HTTPS / REST (Bearer JWT + Idempotency-Key)
                                         v
+-----------------------------------------------------------------------------------+
|                        SECURITY & CONTROLLER TIER (Spring Boot)                   |
|  - JwtAuthenticationFilter: Stateless Bearer token validation & SecurityContext   |
|  - Role-based authorization (@PreAuthorize: ADMIN, ACCOUNT_HOLDER, AUDITOR)       |
|  - GlobalExceptionHandler: Uniform sanitized JSON error responses                |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                            APPLICATION BUSINESS LOGIC TIER                        |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | IdempotencyService: SHA-256 payload digest, state management (IN_PROGRESS,  |  |
|  |                     COMPLETED), cached response return, collision rejection |  |
|  +-----------------------------------------------------------------------------+  |
|                                        |                                          |
|  +-----------------------------------------------------------------------------+  |
|  | TransactionService (Programmatic TransactionTemplate):                      |  |
|  |   1. Validate ownership & active status                                     |  |
|  |   2. Ascending numerical ID sorting (id1 < id2)                             |  |
|  |   3. Pessimistic Write Row Locking (SELECT ... FOR UPDATE)                  |  |
|  |   4. Balance evaluation (balance >= amount)                                 |  |
|  +-----------------------------------------------------------------------------+  |
|                                        |                                          |
|  +-----------------------------------------------------------------------------+  |
|  | DoubleEntryLedgerService:                                                   |  |
|  |   - Creates parent LedgerTransaction (TX-YYYYMMDD-UUID)                     |  |
|  |   - Appends immutable DEBIT entry (negative) + CREDIT entry (positive)      |  |
|  |   - Invariant check: sum(signedAmount) == 0.00                              |  |
|  +-----------------------------------------------------------------------------+  |
|                                        |                                          |
|  +-----------------------------------------------------------------------------+  |
|  | AuditLogService (Propagation.REQUIRES_NEW):                                 |  |
|  |   - Commits non-repudiation audit records regardless of tx outcome          |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                         PERSISTENCE TIER (PostgreSQL 16)                          |
|  - accounts (DECIMAL(19,4), CHECK(balance >= 0), pessimistic row lock targets)    |
|  - transactions & ledger_entries (Immutable write-once financial journal)         |
|  - idempotency_keys (UNIQUE KEY on idempotency_key, SHA-256 hash storage)         |
|  - audit_logs (Autonomous forensic event storage)                                 |
+-----------------------------------------------------------------------------------+
```

---

## 3. Technology Stack

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Language** | Java 21 LTS | Pattern matching, records, virtual threads capability, modern compiler safeguards. |
| **Framework** | Spring Boot 3.3.5 | Production-grade containerization, robust dependency injection, Actuator metrics. |
| **Security** | Spring Security + JJWT 0.12.6 | Stateless JWT authentication, BCrypt password hashing, method-level `@PreAuthorize`. |
| **Data Access** | Spring Data JPA / Hibernate | Object-relational mapping, custom native queries with `LockModeType.PESSIMISTIC_WRITE`. |
| **Database** | PostgreSQL 16+ | ACID compliance, row-level locking, foreign key constraints, strict check constraints. |
| **Testing** | JUnit 5 + Mockito + TestRestTemplate | Multi-threaded concurrency testing (`CountDownLatch`, `ExecutorService`). |
| **Frontend** | HTML5, CSS3, Vanilla JavaScript | Native Fetch API, zero third-party framework overhead, responsive banking layout. |
| **Build Tool** | Apache Maven Wrapper (`mvnw`) | Self-contained, reproducible builds independent of host Maven installation. |

---

## 4. Pre-Seeded Accounts & Demo Credentials

The database seed script (`database/seed.sql`) provisions the following authenticated accounts with BCrypt-hashed passwords (`$2a$10$...`):

### User Credentials
| Role | Username | Password | Purpose |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin` | `Admin@12345` | User management, account freezing, system oversight, audit review |
| **ACCOUNT_HOLDER** | `john_doe` | `User@12345` | Primary customer account holder (Checking & Savings) |
| **ACCOUNT_HOLDER** | `jane_smith`| `User@12345` | Secondary customer account holder (Transfer counterparty) |
| **AUDITOR** | `auditor_jane` | `Auditor@12345` | Read-only compliance officer; financial records & audit viewer |

### Pre-Seeded Financial Accounts
| Account Number | Account Name | Owner | Account Type | Initial Balance | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `LL-1001-JOHN0001` | John Checking | `john_doe` | `CHECKING` | \$5,000.00 | `ACTIVE` |
| `LL-1001-JOHN0002` | John Savings | `john_doe` | `SAVINGS` | \$10,000.00 | `ACTIVE` |
| `LL-1002-JANE0001` | Jane Checking | `jane_smith` | `CHECKING` | \$3,500.00 | `ACTIVE` |
| `SYS-EXTERNAL-DEP-001` | System External Deposit | `SYSTEM` | `SYSTEM_CLEARING`| \$0.00 | `ACTIVE` |
| `SYS-EXTERNAL-WTH-001` | System External Withdrawal| `SYSTEM` | `SYSTEM_CLEARING`| \$0.00 | `ACTIVE` |

---

## 5. Step-by-Step Database Setup

### Step 5.1: Verify PostgreSQL Installation
Ensure PostgreSQL is installed and running on `localhost:5432`.
```powershell
# Verify PostgreSQL Service status (Windows)
Get-Service PostgreSQL*
```

### Step 5.2: Create Database & Execute Schema
Open your terminal (or PostgreSQL Workbench / CLI) and run:
```bash
# Connect to PostgreSQL as root or administrative user
psql -h localhost -U postgres
```

Execute the database creation and schema script:
```sql
CREATE DATABASE ledgerline;
psql -h localhost -U postgres -d ledgerline

```

*Alternatively, from the command line in the project root:*
```powershell
psql -h localhost -U postgres -d ledgerline -f database/schema.sql
psql -h localhost -U postgres -d ledgerline -f database/seed.sql
```

### Step 5.3: Verify Schema & Records
```sql
\dt
-- Expected output: accounts, audit_logs, idempotency_keys, ledger_entries, transactions, users

SELECT account_number, account_name, balance, status FROM accounts;
```

---

## 6. Step-by-Step Backend Execution

### Step 6.1: Configure Database Credentials
Inspect `backend/src/main/resources/application.yml`. If your PostgreSQL root password is not blank or `root`, update it or set the environment variables:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ledgerline
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:root}
    driver-class-name: org.postgresql.Driver
```

Or provide them via shell environment:
```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "YourPasswordHere"
```

### Step 6.2: Compile & Run the Backend
Navigate to the `backend/` directory and execute the Spring Boot application using the bundled Maven Wrapper:
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
*(On Linux/macOS, use `./mvnw spring-boot:run`)*

The Spring Boot backend will start on **http://localhost:8082**.
Look for the console log:
```
Started LedgerlineApplication in X.XXX seconds (process running for X.XXX)
```

---

## 7. Step-by-Step Frontend Execution

The frontend is built purely with standards-compliant HTML5, CSS3, and JavaScript (Fetch API). No Node.js build step or npm install is required.

### Option A: VS Code Live Server (Recommended)
1. Open the project root in VS Code.
2. Right-click on `frontend/index.html` and select **"Open with Live Server"** (port 5500).
3. The browser will open to `http://127.0.0.1:5500/frontend/index.html`.

### Option B: Python Simple HTTP Server
Open a terminal in the `frontend/` directory:
```powershell
cd frontend
python -m http.server 3000
```
Open your browser and navigate to **http://localhost:3000/index.html**.

### Option C: Direct File Opening
Double-click `frontend/index.html` or `frontend/login.html` directly in your file explorer. All API calls target `http://localhost:8082/api` with full CORS support pre-configured in Spring Security.

---

## 8. REST API Specification

All protected endpoints require the HTTP header:
`Authorization: Bearer <jwt-token>`

All financial mutation endpoints (`/deposit`, `/withdraw`, `/transfer`) support the optional header:
`Idempotency-Key: <uuid-v4>`

### 8.1 Authentication Endpoints (`/api/auth`)
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Registers a new account holder. Roles are strictly server-assigned as `ROLE_ACCOUNT_HOLDER`. |
| `POST` | `/api/auth/login` | Public | Authenticates credentials; returns Bearer JWT token and user profile. |
| `GET` | `/api/auth/me` | Authenticated | Retrieves current authenticated user profile from token context. |

### 8.2 Account Endpoints (`/api/accounts`)
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/accounts` | `ACCOUNT_HOLDER`, `AUDITOR` | Lists active accounts owned by the user (or all non-system accounts for auditor). |
| `POST` | `/api/accounts` | `ACCOUNT_HOLDER`, `ADMIN` | Opens a new checking or savings account. |
| `GET` | `/api/accounts/{accountNumber}` | Authenticated | Retrieves account metadata and current balance with ownership check. |

### 8.3 Transaction Endpoints (`/api/transactions`)
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/transactions/deposit` | `ACCOUNT_HOLDER` | Deposits funds via `SYS-EXTERNAL-DEP-001` with balanced zero-sum journal entries. |
| `POST` | `/api/transactions/withdraw` | `ACCOUNT_HOLDER` | Withdraws funds via `SYS-EXTERNAL-WTH-001` under pessimistic row lock. |
| `POST` | `/api/transactions/transfer` | `ACCOUNT_HOLDER` | Internal wire transfer between accounts with ascending ID lock ordering. |
| `GET` | `/api/transactions/history` | Authenticated | Returns chronological transaction ledger entries for user accounts. |

### 8.4 Statement Endpoints (`/api/statements`)
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/statements/account/{accountNumber}` | Authenticated | Generates statement with running balances; supports `startDate` and `endDate` query params. |

### 8.5 Administration & Compliance Endpoints
| Method | Endpoint | Allowed Roles | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/users` | `ADMIN` | Lists all registered users and roles. |
| `GET` | `/api/admin/accounts` | `ADMIN` | Lists all accounts system-wide. |
| `PATCH`| `/api/admin/accounts/{id}/status`| `ADMIN` | Updates account status (`ACTIVE`, `FROZEN`, `CLOSED`). |
| `GET` | `/api/audit/logs` | `ADMIN`, `AUDITOR` | Retrieves full immutable security and non-repudiation audit trail. |

---

## 9. Automated Testing Suite

The backend includes a comprehensive, multi-threaded automated test suite built on **JUnit 5, Mockito, and Spring Boot Test**.

### Test Suite Execution
To execute the automated test suite:
```powershell
cd backend
.\mvnw.cmd test
```

### Coverage of Financial & Concurrency Test Classes
| Test Class | Threads | Scenarios Tested |
| :--- | :--- | :--- |
| **`MoneyConservationTest`** | Single | Mathematical proof: $\sum \text{signedAmount} = 0.00$ across all transfers. |
| **`NoNegativeBalanceTest`** | Single | Overdraft prevention, zero/negative amount rejection, balance preservation. |
| **`ConcurrentWithdrawalTest`**| 10 Threads | Simultaneous race condition against \$1,000 balance; exactly 1 succeeds, 9 fail with `InsufficientFundsException`. |
| **`ConcurrentTransferTest`** | 8 Threads | 80 mutual transfers ($A \leftrightarrow B$); zero deadlocks; 100% money conservation. |
| **`IdempotencyDuplicateTest`**| Single | Cached response return on duplicate request; rejection of modified payload (409 Conflict). |
| **`IdempotencySimultaneousTest`**| 2 Threads | Simultaneous in-flight race on same key; exactly 1 processes, 1 receives 409 Conflict. |
| **`UnauthorizedAccountAccessTest`**| Single | BOLA / IDOR protection; user cannot view or withdraw from an unauthorized account. |
| **`UnauthorizedTransferTest`** | Single | User cannot specify another user's account as source of transfer. |
| **`AuditorReadOnlyTest`** | Single | Auditor successfully reads history/audit logs; rejected (HTTP 403) on mutation. |
| **`LedgerImmutabilityTest`** | Single | Direct JPA updates on historical ledger records fail with constraint errors. |

**Current Test Results:** **15 Tests Run, 0 Failures, 0 Errors, 0 Skipped (100% Pass Rate).**

---

## 10. 5-Minute Video Demonstration Script

Use the following outline and script for the 5-minute video demonstration submission:

### Minute 0:00 – 1:00: Architecture & Technology Stack Overview
- **Visual:** Show project repository structure in IDE and `README.md`.
- **Script:** *"Welcome. My name is Naga Sneha, presenting LEDGERLINE for the Snovasys Fresher Hiring Challenge 2026. LedgerLine is a high-integrity financial transaction engine engineered in Java 21 LTS and Spring Boot 3.3.5. Its architecture enforces three non-negotiable principles: strict zero-sum double-entry bookkeeping, deadlock-free pessimistic write locking with ascending ID sorting, and cryptographic SHA-256 idempotency deduplication."*

### Minute 1:00 – 2:15: User Authentication & Role-Based Cockpit
- **Visual:** Open `frontend/login.html`. Demonstrate the quick-fill buttons.
- **Action 1:** Log in as `john_doe` (`ROLE_ACCOUNT_HOLDER`).
- **Script:** *"We log in as John Doe, an Account Holder. The dashboard displays John's active checking and savings balances, calculated dynamically from the ledger. Notice the dynamic sidebar: John can deposit, withdraw, transfer, and view his personal statement."*
- **Action 2:** Log in as `admin` (`ROLE_ADMIN`).
- **Script:** *"Switching to Administrator, the dashboard transforms into an operational command center displaying system-wide customer liquidity, registered users, and account management tools to freeze or close accounts."*
- **Action 3:** Log in as `auditor_jane` (`ROLE_AUDITOR`).
- **Script:** *"Logging in as Auditor Jane, the interface is strictly read-only. Jane can inspect ledger entries and security audit logs, but has zero financial mutation privileges."*

### Minute 2:15 – 3:30: Live Financial Mutations & Double-Entry Invariant
- **Visual:** Switch back to `john_doe`.
- **Action 1 (Deposit):** Go to Deposit Funds. Select Checking account, enter \$500.00. Show confirmation modal. Click Confirm.
- **Script:** *"When John deposits \$500.00, LedgerLine creates a balanced journal entry: debiting the System External Deposit clearing account and crediting John's checking account."*
- **Action 2 (Transfer):** Go to Money Transfer. Transfer \$250.00 from John's Checking (`LL-1001-JOHN0001`) to Jane's Checking (`LL-1002-JANE0001`). Show the UUID Idempotency-Key generated in browser DevTools Network tab.
- **Script:** *"Notice the wire transfer: our backend locks John's and Jane's accounts in strictly ascending primary key order to prevent deadlocks. In a single atomic transaction, John is debited \$250.00, Jane is credited \$250.00, and the sum of signed entries equals exactly zero."*
- **Action 3 (Statement):** Navigate to Account Statements. Generate statement for John's Checking account.
- **Script:** *"The statement displays each ledger posting alongside a mathematically verified running balance."*

### Minute 3:30 – 4:30: Concurrency, Idempotency & Audit Trail
- **Visual:** Open `frontend/audit.html` (as Auditor or Admin).
- **Script:** *"Every operation—and every refused attempt—is committed autonomously to the audit logs via Propagation.REQUIRES_NEW. If John attempts to withdraw \$50,000.00, the system rejects the transaction with InsufficientFundsException and records the refused attempt with client IP."*
- **Visual:** Switch to terminal. Run `./mvnw test`.
- **Script:** *"To verify concurrency under high load, our automated test suite executes 10 simultaneous threads competing for a single \$1,000 balance in ConcurrentWithdrawalTest, and 80 mutual transfers across 8 threads in ConcurrentTransferTest. All 15 tests pass with zero deadlocks and zero overdrafts."*

### Minute 4:30 – 5:00: Summary & Conclusion
- **Visual:** Show `THREATS.md` STRIDE matrix and GitHub commit history.
- **Script:** *"In conclusion, LedgerLine delivers institutional-grade financial integrity, defense-in-depth threat mitigation, and clean, modular code. Thank you for evaluating my submission for the Snovasys Fresher Hiring Challenge 2026."*

---

## 11. Production Hardening Checklist

When transitioning LedgerLine to production environments:
1. **TLS / HTTPS Termination:** Configure an SSL certificate or reverse proxy (Nginx / AWS ALB) terminating TLS 1.3 on port 443.
2. **JWT Secret Rotation:** Replace the default HMAC-SHA256 secret key in `application.yml` with a cryptographically random 512-bit key injected via environment variable `JWT_SECRET`.
3. **Database Privileges:** Restrict the application database user to `SELECT`, `INSERT`, `UPDATE` permissions on business tables. Deny `DELETE` and `DROP` privileges to guarantee ledger append-only immutability at the PostgreSQL user level.
4. **HikariCP Sizing:** Set `maximum-pool-size` according to the formula:
   $$\text{Pool Size} = (\text{Core CPU Count} \times 2) + \text{Effective Spindle Count}$$
5. **CORS Whitelisting:** Replace `allowedOrigins("*")` in `SecurityConfig.java` with the explicit production domain (e.g., `https://banking.ledgerline.com`).
