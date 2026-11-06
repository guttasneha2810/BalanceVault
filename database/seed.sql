-- =====================================================================
-- LEDGERLINE DATABASE SEED SCRIPT
-- =====================================================================

USE ledgerline;

-- Disable foreign key checks for clean idempotent seeding if needed
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE audit_logs;
TRUNCATE TABLE idempotency_keys;
TRUNCATE TABLE ledger_entries;
TRUNCATE TABLE ledger_transactions;
TRUNCATE TABLE accounts;
TRUNCATE TABLE users;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- 1. Seed Users
-- Passwords:
-- admin@ledgerline.com      : Admin@12345
-- john.doe@ledgerline.com   : User@12345
-- auditor@ledgerline.com    : Auditor@12345
-- jane.smith@ledgerline.com : User@12345
-- ---------------------------------------------------------------------
INSERT INTO users (id, username, email, password_hash, full_name, role, status, created_at, updated_at) VALUES
(1, 'admin', 'admin@ledgerline.com', '$2a$10$9FBPoeel3/J3PkAe4v76FOoCbYHD1pxThPPswWmFto72jRgWtZxUS', 'System Administrator', 'ROLE_ADMIN', 'ACTIVE', NOW(), NOW()),
(2, 'john.doe', 'john.doe@ledgerline.com', '$2a$10$r0ftF0PQw942575Ob.ycg./7kckxyWCkweLwJRF1svR8.hnhBkovu', 'John Doe', 'ROLE_ACCOUNT_HOLDER', 'ACTIVE', NOW(), NOW()),
(3, 'auditor', 'auditor@ledgerline.com', '$2a$10$1EEYR2vd3Rh6sMDyiBZ33O2a9NnRfL.srWmp/qM5phvEPWCWoZiVy', 'Lead Compliance Auditor', 'ROLE_AUDITOR', 'ACTIVE', NOW(), NOW()),
(4, 'jane.smith', 'jane.smith@ledgerline.com', '$2a$10$r0ftF0PQw942575Ob.ycg./7kckxyWCkweLwJRF1svR8.hnhBkovu', 'Jane Smith', 'ROLE_ACCOUNT_HOLDER', 'ACTIVE', NOW(), NOW());

-- ---------------------------------------------------------------------
-- 2. Seed Accounts
-- System Clearing Accounts (is_system_account = TRUE)
-- User Accounts (is_system_account = FALSE)
-- ---------------------------------------------------------------------
INSERT INTO accounts (id, account_number, user_id, account_name, account_type, balance, currency, status, is_system_account, version, created_at, updated_at) VALUES
(1, 'SYS-EXTERNAL-DEP-001', NULL, 'External Deposit Clearing Account', 'SYSTEM_CLEARING', -20500.0000, 'USD', 'ACTIVE', TRUE, 0, NOW(), NOW()),
(2, 'SYS-EXTERNAL-WTH-001', NULL, 'External Withdrawal Clearing Account', 'SYSTEM_CLEARING', 0.0000, 'USD', 'ACTIVE', TRUE, 0, NOW(), NOW()),
(3, 'ACC1000000001', 2, 'John Doe Primary Checking', 'CHECKING', 5000.0000, 'USD', 'ACTIVE', FALSE, 0, NOW(), NOW()),
(4, 'ACC1000000002', 2, 'John Doe High Yield Savings', 'SAVINGS', 12500.0000, 'USD', 'ACTIVE', FALSE, 0, NOW(), NOW()),
(5, 'ACC2000000001', 4, 'Jane Smith Everyday Checking', 'CHECKING', 3000.0000, 'USD', 'ACTIVE', FALSE, 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 3. Seed Ledger Transactions (Double-Entry Balanced)
-- Initial Opening Balances
-- ---------------------------------------------------------------------
INSERT INTO ledger_transactions (id, transaction_reference, transaction_type, amount, currency, status, description, initiated_by_user_id, idempotency_key, created_at) VALUES
(1, 'TXN-INIT-001-DEP', 'DEPOSIT', 5000.0000, 'USD', 'COMPLETED', 'Initial opening balance deposit for John Doe Checking', 1, 'INIT-DEP-001', NOW()),
(2, 'TXN-INIT-002-DEP', 'DEPOSIT', 12500.0000, 'USD', 'COMPLETED', 'Initial opening balance deposit for John Doe Savings', 1, 'INIT-DEP-002', NOW()),
(3, 'TXN-INIT-003-DEP', 'DEPOSIT', 3000.0000, 'USD', 'COMPLETED', 'Initial opening balance deposit for Jane Smith Checking', 1, 'INIT-DEP-003', NOW());

-- ---------------------------------------------------------------------
-- 4. Seed Ledger Entries (Debit & Credit Zero-Sum Postings)
-- Entry Type: DEBIT (negative signed amount), CREDIT (positive signed amount)
-- ---------------------------------------------------------------------
INSERT INTO ledger_entries (id, transaction_id, account_id, entry_type, amount, signed_amount, balance_after, created_at) VALUES
-- Transaction 1: $5,000 to John Doe Checking
(1, 1, 1, 'DEBIT', 5000.0000, -5000.0000, -5000.0000, NOW()),
(2, 1, 3, 'CREDIT', 5000.0000, 5000.0000, 5000.0000, NOW()),

-- Transaction 2: $12,500 to John Doe Savings
(3, 2, 1, 'DEBIT', 12500.0000, -12500.0000, -17500.0000, NOW()),
(4, 2, 4, 'CREDIT', 12500.0000, 12500.0000, 12500.0000, NOW()),

-- Transaction 3: $3,000 to Jane Smith Checking
(5, 3, 1, 'DEBIT', 3000.0000, -3000.0000, -20500.0000, NOW()),
(6, 3, 5, 'CREDIT', 3000.0000, 3000.0000, 3000.0000, NOW());

-- ---------------------------------------------------------------------
-- 5. Seed Initial Audit Logs
-- ---------------------------------------------------------------------
INSERT INTO audit_logs (id, event_type, username, client_ip, account_number, transaction_reference, status, failure_reason, details, timestamp) VALUES
(1, 'SYSTEM_INITIALIZATION', 'system', '127.0.0.1', NULL, NULL, 'SUCCESS', NULL, 'Database schema seeded with default roles and accounts', NOW()),
(2, 'ACCOUNT_CREATED', 'admin', '127.0.0.1', 'ACC1000000001', 'TXN-INIT-001-DEP', 'SUCCESS', NULL, 'Created checking account for John Doe with initial deposit', NOW()),
(3, 'ACCOUNT_CREATED', 'admin', '127.0.0.1', 'ACC1000000002', 'TXN-INIT-002-DEP', 'SUCCESS', NULL, 'Created savings account for John Doe with initial deposit', NOW()),
(4, 'ACCOUNT_CREATED', 'admin', '127.0.0.1', 'ACC2000000001', 'TXN-INIT-003-DEP', 'SUCCESS', NULL, 'Created checking account for Jane Smith with initial deposit', NOW());
