-- 1. Create Roles Table
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    UNIQUE KEY uq_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create Users Table
CREATE TABLE users (
    id CHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY idx_users_email (email),
    CONSTRAINT fk_users_role_id FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Create Crypto Keys Table
CREATE TABLE crypto_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version INT NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    UNIQUE KEY idx_keys_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Create Vault Records Table
CREATE TABLE vault_records (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    encrypted_data MEDIUMBLOB NOT NULL,
    algorithm_used VARCHAR(50) NOT NULL,
    key_version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_vault_records_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_vault_records_key_version FOREIGN KEY (key_version) REFERENCES crypto_keys (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Create Audit Logs Table
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id CHAR(36) DEFAULT NULL,
    action VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    timestamp DATETIME(6) NOT NULL,
    CONSTRAINT fk_audit_logs_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Create Indexes
CREATE INDEX idx_vault_user ON vault_records (user_id);
CREATE INDEX idx_audit_user ON audit_logs (user_id);
CREATE INDEX idx_audit_timestamp ON audit_logs (timestamp);
