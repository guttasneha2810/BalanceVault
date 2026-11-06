-- =====================================================================
-- LEDGERLINE DATABASE SCHEMA
-- Compatible with MySQL 8.0+ and ANSI SQL standard
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ledgerline
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE ledgerline;

-- ---------------------------------------------------------------------
-- 1. Table: users
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email),
    INDEX idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. Table: accounts
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_system_account BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_account_balance_non_negative CHECK (is_system_account = TRUE OR balance >= 0),
    INDEX idx_accounts_number (account_number),
    INDEX idx_accounts_user (user_id),
    INDEX idx_accounts_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. Table: ledger_transactions
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_reference VARCHAR(64) NOT NULL UNIQUE,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    description VARCHAR(255) NOT NULL,
    initiated_by_user_id BIGINT NULL,
    idempotency_key VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_user FOREIGN KEY (initiated_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_transactions_ref (transaction_reference),
    INDEX idx_transactions_type (transaction_type),
    INDEX idx_transactions_created (created_at),
    INDEX idx_transactions_user (initiated_by_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. Table: ledger_entries
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount DECIMAL(19, 4) NOT NULL,
    signed_amount DECIMAL(19, 4) NOT NULL, -- Positive for Credit, Negative for Debit
    balance_after DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entries_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transactions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_entries_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    INDEX idx_entries_account_created (account_id, created_at),
    INDEX idx_entries_transaction (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. Table: idempotency_keys
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING', -- 'PROCESSING', 'COMPLETED', 'FAILED'
    response_code INT NULL,
    response_body LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_idempotency_key (idempotency_key),
    INDEX idx_idempotency_user_key (user_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 6. Table: audit_logs
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    username VARCHAR(50) NULL,
    client_ip VARCHAR(45) NULL,
    account_number VARCHAR(34) NULL,
    transaction_reference VARCHAR(64) NULL,
    status VARCHAR(20) NOT NULL, -- 'SUCCESS', 'FAILURE'
    failure_reason VARCHAR(255) NULL,
    details TEXT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_event (event_type),
    INDEX idx_audit_username (username),
    INDEX idx_audit_account (account_number),
    INDEX idx_audit_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
