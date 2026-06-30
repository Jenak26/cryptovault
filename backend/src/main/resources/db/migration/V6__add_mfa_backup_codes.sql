-- One-time MFA recovery codes. Only the SHA-256 hash of each code is stored; the plaintext is shown
-- to the user exactly once at generation time. A code is single-use (marked used on redemption).
CREATE TABLE mfa_backup_codes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    CHAR(36)    NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_backup_codes_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_backup_user_hash (user_id, code_hash),
    INDEX idx_backup_user_used (user_id, used)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
