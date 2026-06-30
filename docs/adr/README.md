# Architecture Decision Records

Short records of the **why** behind CryptoVault's significant design choices. Each ADR captures the
context, the decision, its consequences, and the alternatives that were weighed.

Format: [Michael Nygard's ADR template](https://github.com/joelparkerhenderson/architecture-decision-record).

| # | Decision |
|---|---|
| [0001](0001-hkdf-for-kek-derivation.md) | Derive the KEK with HKDF-SHA256, not a raw hash |
| [0002](0002-crypto-agility-via-strategy.md) | Crypto-agility via a strategy interface + per-record algorithm tag |
| [0003](0003-envelope-encryption-env-master-secret.md) | Envelope encryption with an env-var master secret (KMS/HSM stand-in) |
| [0004](0004-stateless-jwt-with-redis-blacklist.md) | Stateless JWT + Redis `jti` blacklist for real logout |
| [0005](0005-totp-mfa-from-scratch.md) | Implement TOTP MFA from scratch, with Redis challenges and backup codes |
| [0006](0006-flyway-owns-the-schema.md) | Flyway owns the schema; Hibernate runs `ddl-auto=validate` |
