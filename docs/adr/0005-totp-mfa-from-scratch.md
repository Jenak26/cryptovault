# 5. Implement TOTP MFA from scratch, with Redis challenges and backup codes

**Status:** Accepted

## Context
Banks expect MFA. TOTP (RFC 6238) is the ubiquitous app-based second factor. A library would work,
but implementing the algorithm directly demonstrates understanding of the primitive and avoids a
dependency for what is, at its core, an HMAC + truncation.

## Decision
Implement TOTP from scratch in `TotpService` (HOTP per RFC 4226 + the time step, plus Base32). MFA is
**opt-in per user**. When enabled, login is two-step:
1. Password step succeeds → instead of a JWT, issue a **single-use challenge token** stored in Redis
   (5-minute TTL) via `MfaChallengeStore`.
2. The client submits that token + a code to `/api/auth/mfa/verify`; only then is the JWT issued.

Enrolling issues **ten one-time backup codes** — only their SHA-256 hashes are stored — which can be
redeemed in place of a TOTP code, regenerated, and are cleared when MFA is disabled.

## Consequences
- No third-party MFA dependency; the algorithm is explicit and testable (incl. RFC-style vectors).
- The challenge token means a correct password alone never yields a session for MFA users, and the
  challenge can't be replayed (single-use, short TTL).
- Backup codes remove the "lost phone = locked out forever" failure mode.
- A `±1` time-step skew window is allowed to tolerate clock drift (a standard trade-off).

## Alternatives considered
- **A TOTP library** — fine in production; rejected here to demonstrate the mechanism and avoid a dep.
- **Mandatory MFA for everyone** — rejected: opt-in is the right default for this app's users.
- **Returning the JWT then "stepping up"** — rejected: issuing any token before the second factor
  widens the window for misuse.
