# 3. Envelope encryption with an env-var master secret (KMS/HSM stand-in)

**Status:** Accepted

## Context
Encrypting every record directly under one long-lived key makes key rotation effectively impossible
(you'd have to re-encrypt all data to rotate) and concentrates blast radius. Production systems solve
this with a KMS/HSM, but this is a portfolio project that should demonstrate the *mechanics* without
a paid managed service.

## Decision
Use **envelope encryption**: per-version **data keys (DEKs)** encrypt records; a single **KEK**
(derived from the master secret — see ADR 0001) wraps the DEKs. Only wrapped DEKs are stored
(`crypto_keys.encrypted_key`); raw key material is never written to the database or logs. The master
secret comes from the `CRYPTOVAULT_MASTER_SECRET` environment variable.

## Consequences
- Rotating the active DEK is cheap: generate a new version, keep old versions to decrypt old data.
- A DB leak exposes only wrapped keys + ciphertext, not plaintext keys.
- The `KeyManager.getKek()` boundary is the single seam where a real KMS/HSM would plug in.
- **Known limitation:** the KEK lives in the app process; a memory-scraping attacker on the host
  could recover it. This is the main gap vs. a hardware-backed KMS — documented in `SECURITY.md`.

## Alternatives considered
- **Managed KMS (AWS KMS / Vault) now** — the production-correct answer, deferred to keep the project
  self-contained and free to run; the seam is in place to adopt it later.
- **Encrypt records directly under the master key** — rejected: no clean rotation story, larger blast
  radius.
