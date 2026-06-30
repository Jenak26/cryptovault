# 4. Stateless JWT + Redis `jti` blacklist for real logout

**Status:** Accepted

## Context
Stateless JWTs scale well (no session store on the hot path) but have a well-known weakness: a signed
token is valid until it expires, so "logout" normally can't actually revoke it. For a secrets vault,
a logout that doesn't really invalidate the token is unacceptable.

## Decision
Issue stateless HS256 JWTs carrying a unique `jti`. On logout, write the `jti` to a **Redis blacklist**
(`bl:jti:<id>`) with a TTL equal to the token's remaining lifetime. The `JwtAuthFilter` checks the
blacklist on every request, so a logged-out token is rejected — and the entry self-expires exactly
when the token would have anyway, so the blacklist can't grow unbounded.

## Consequences
- Logout genuinely revokes access; "logged out" means rejected, not just forgotten client-side.
- The common path stays stateless; Redis is touched only to check/insert a small key.
- Access tokens are short-lived (1 hour) with no refresh tokens, keeping the revocation surface small.

## Alternatives considered
- **Pure stateless JWT, no revocation** — rejected: can't honour logout, unacceptable for this domain.
- **Server-side sessions** — rejected: gives up the statelessness that motivated JWTs, and adds a
  session store to every request.
- **Rotating refresh tokens / JWKS key rotation** — the production hardening path; out of scope for
  the MVP and noted as a simplification in `SECURITY.md`.
