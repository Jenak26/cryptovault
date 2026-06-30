-- TOTP-based multi-factor authentication, opt-in per user.
-- mfa_secret holds the Base32-encoded TOTP shared secret (NULL until enrollment starts).
-- mfa_enabled gates whether login requires a second factor.
ALTER TABLE users
    ADD COLUMN mfa_secret  VARCHAR(64) NULL,
    ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
