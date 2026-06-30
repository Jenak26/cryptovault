# Security Model

This document describes CryptoVault's security design, the reasoning behind each cryptographic
choice, and — just as importantly — an honest list of the simplifications that separate this
project from a production system at a bank.

> **One-line scope statement:** CryptoVault is a from-scratch implementation of the patterns a
> managed KMS/HSM provides. It is built to demonstrate understanding of those mechanics, **not**
> to replace a real KMS/HSM in production.

---

## 1. Key hierarchy (envelope encryption)

```
  CRYPTOVAULT_MASTER_SECRET  (high-entropy secret, supplied via environment variable)
            │
            ▼  HKDF-SHA256  (salt + info label, RFC 5869)
     Master KEK  (256-bit Key-Encrypting Key, derived deterministically, never stored)
            │
            ▼  AES-256-GCM wrap
   Data Keys (DEKs)  — one per version, stored only in wrapped form in crypto_keys.encrypted_key
            │
            ▼  active DEK encrypts
      Vault records  (each row records the algorithm + key version it was written with)
```

Only **wrapped** key material is ever persisted. The KEK is re-derived in memory on startup and
never written to the database or logs.

---

## 2. Cryptographic choices and rationale

| Decision | Choice | Why |
|---|---|---|
| KEK derivation | **HKDF-SHA256** with a fixed salt + info label | The master secret is high-entropy, so a proper KDF (extract-then-expand) with domain separation is the right tool. A raw `SHA-256(secret)` — which this project originally used — has **no salt and no domain separation** and is not a KDF. **If the master secret were a human password**, this would instead need a slow, memory-hard KDF such as **Argon2id / scrypt / PBKDF2**. |
| Key wrapping | AES-256-GCM | Authenticated encryption (AEAD): tampering with a wrapped key is detected on unwrap. |
| Data encryption | Pluggable: **AES-256-GCM** or **ChaCha20-Poly1305** | Both are AEAD. Crypto-agility lets the active cipher change via config without code changes or breaking existing records. |
| Nonce / IV | Fresh 12-byte random value **per encryption** | GCM/Poly1305 security collapses on nonce reuse under the same key. Each record carries its own nonce prepended to the ciphertext. |
| Per-record metadata | `algorithm_used` + `key_version` stored on every row | A record is always decrypted with the exact algorithm and key version it was written with, which is what makes rotation and cipher migration non-destructive. |
| Password hashing | **BCrypt** (strength 10) | Standard, salted, adaptive password hashing. |
| Tokens | **JWT, HMAC-SHA256**, claims `userId`, `role`, `jti`, `exp`; 1-hour lifetime | Stateless auth. The `jti` enables real revocation (see below). |

---

## 3. Authentication, sessions, and revocation

- **Register / login** use BCrypt. Login returns a **generic 401** for both unknown email and wrong
  password, so the API never reveals whether an account exists (no user enumeration).
- **Logout is real, not cosmetic.** It adds the token's `jti` to a Redis blacklist
  (`bl:jti:<id>`, TTL = the token's remaining lifetime). The `JwtAuthFilter` checks this blacklist
  on every request, so a logged-out token is rejected even though JWTs are otherwise stateless.
- **`JwtAuthEntryPoint`** returns a clean `401` (not `403`) for unauthenticated access.
- **Optional TOTP MFA.** Users can enrol a second factor (RFC 6238 TOTP, implemented from scratch
  in `TotpService`). When enabled, login is two-step: the password step returns a short-lived,
  single-use challenge (Redis, 5-min TTL) instead of a token, and the JWT is only issued after a
  valid code is submitted to `/api/auth/mfa/verify`. Both steps fail with the same generic 401.

---

## 4. Authorization (RBAC)

- Method security (`@EnableMethodSecurity`); admin routes are gated with
  `@PreAuthorize("hasRole('ADMIN')")`.
- A `USER` token on an admin route returns `403`; ownership is enforced on vault reads/deletes
  (a user cannot access another user's records).

## 5. Brute-force protection

- A Redis-backed rate limiter tracks failed logins **separately by IP and by email**, locking out
  after 5 failures for 15 minutes, and returns `429`. Successful login resets the counters.

## 6. Audit logging

- Every security-relevant action (`REGISTER`, `LOGIN_SUCCESS`, `LOGIN_FAIL`, `LOGOUT`,
  `VAULT_STORE/READ/DELETE`, `KEY_ROTATE`) writes an audit row with a proxy-aware IP.
- Failed logins against an **unknown** email are logged with a `NULL` user id, so reconnaissance
  attempts are still recorded.

---

## 7. Threat model (STRIDE-lite)

| Threat | Vector | Mitigation in CryptoVault |
|---|---|---|
| **Spoofing** | Stolen / forged token | HMAC-signed JWTs; signature + expiry verified every request; `jti` blacklist on logout. |
| **Tampering** | Altered ciphertext or wrapped key | AEAD (GCM / Poly1305) — any modification fails the auth tag on decrypt. |
| **Repudiation** | "I didn't do that" | Append-only audit log of all security events with IP + timestamp. |
| **Information disclosure** | DB exfiltration | Data encrypted at rest under versioned DEKs; DEKs stored only wrapped; KEK never persisted; passwords BCrypt-hashed; secrets/keys never logged. |
| **Information disclosure** | User enumeration | Generic 401 on login; same response for unknown email and bad password. |
| **Denial of service** | Credential stuffing / brute force | Per-IP and per-email login rate limiting with lockout. |
| **Elevation of privilege** | User reaching admin actions | RBAC via method security; ownership checks on vault records. |

---

## 8. Simplifications vs. a production system (read this before judging the crypto)

These are deliberate, known gaps — naming them is the point:

1. **No HSM / KMS.** The master secret lives in an environment variable and the KEK is derived in
   the app process. Production would store the KEK in an **HSM** or a managed **KMS**
   (AWS KMS, GCP KMS, HashiCorp Vault) and never expose raw key bytes to the application; the
   `getKek()` boundary in `KeyManager` is exactly where that integration would slot in.
2. **Single JWT signing secret, no rotation, no refresh tokens.** Production would rotate signing
   keys (JWKS) and use short-lived access tokens + rotating refresh tokens.
3. **No transport security in the app itself.** TLS is assumed to be terminated by a load
   balancer / ingress in front of the service; the app speaks plain HTTP locally.
4. **No recovery/backup codes for MFA.** TOTP enrolment works, but a lost authenticator currently
   has no self-service recovery path (production would issue one-time backup codes).
5. **Decrypted DEKs are cached in process memory** for performance. Production would weigh this
   against memory-scraping risk and consider per-operation KMS calls or memory hygiene.
6. **No key escrow / backup-and-restore or break-glass procedure** for the master secret.
7. **Rate limiting covers login only**, not every endpoint.

---

## 9. Reporting

This is a portfolio / learning project. If you spot a security issue, please open a GitHub issue
describing it — there is no production deployment handling real user data.
