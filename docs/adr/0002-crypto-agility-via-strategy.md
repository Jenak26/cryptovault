# 2. Crypto-agility via a strategy interface + per-record algorithm tag

**Status:** Accepted

## Context
Ciphers get deprecated. A vault that hard-codes one algorithm can't migrate without a risky
big-bang re-encryption, and can't decrypt old data once the code changes. The product goal is to
swap the active cipher without code changes and without breaking existing records.

## Decision
Define a `CipherStrategy` interface (`encrypt`, `decrypt`, `name`) with `AesGcmStrategy` and
`ChaCha20Poly1305Strategy` implementations, wired into a `CryptoEngine` that holds a
`Map<String, CipherStrategy>`. The **active** algorithm is config-driven; **every `vault_record`
stores the `algorithm_used` and `key_version` it was written with**. Encryption uses the active
strategy; decryption looks up the strategy named on the record.

## Consequences
- The active cipher is a one-line config change — new writes use it, old rows still decrypt.
- Adding a third algorithm is a new class + a map entry; no changes to call sites.
- A background re-encryption job can migrate old records onto the active algorithm/key on demand.
- Slight storage overhead: an algorithm tag + key version per record (worth it for agility).

## Alternatives considered
- **Single hard-coded cipher** — rejected: no migration path, no agility.
- **Algorithm inferred globally from config** — rejected: would make already-stored records
  undecryptable the moment the config changes. The per-record tag is what makes switching safe.
