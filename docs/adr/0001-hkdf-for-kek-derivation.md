# 1. Derive the KEK with HKDF-SHA256, not a raw hash

**Status:** Accepted

## Context
The Key-Encrypting-Key (KEK) that wraps every data key is derived from a single high-entropy master
secret supplied via an environment variable. The first implementation derived it as
`SHA-256(masterSecret)`. A raw hash is **not** a key-derivation function: it has no salt and no
domain separation, so the same secret always yields the same bytes for any purpose, and there's no
standardised construction binding the key to its use.

## Decision
Derive the KEK with **HKDF-SHA256** (RFC 5869) using a fixed, non-secret salt and an `info` label
that names the purpose (`cryptovault-master-kek/aes-256-gcm/v1`). The salt/info are fixed so the KEK
re-derives deterministically on every startup without ever being stored.

## Consequences
- Proper extract-then-expand derivation with domain separation; the derived key is bound to its role.
- KEK is never persisted — it's recomputed in memory from the master secret on boot.
- The `info` label is versioned, leaving room to rotate the derivation scheme later.

## Alternatives considered
- **Raw `SHA-256(secret)`** — rejected: not a KDF (no salt, no domain separation).
- **Argon2id / PBKDF2** — the right tool **if** the master secret were a human password (low entropy,
  needs a slow memory-hard KDF). Here the secret is high-entropy, so HKDF is the correct, faster fit.
- **Random salt stored alongside** — unnecessary: a fixed salt is acceptable for deterministic
  derivation from a high-entropy secret, and storing a salt adds state without security benefit here.
