# Phase 2 — Authentication Core: Design

> **Status:** Approved 2026-06-20 · **Updated 2026-06-21:** JWT filter + protected `/api/me` pulled
> into Phase 2 so logout enforcement is real (see "Scope amendment" below).
> **Phase:** 2 of the CryptoVault MVP (`docs/PLAN.md` §4)
> **Goal:** Register, login, and logout working with bcrypt-hashed passwords, JWTs, and a Redis token blacklist — with bearer tokens validated and rejected on protected routes.

## Scope amendment (2026-06-21)

The original split deferred the JWT validation filter to Phase 3, but Phase 2's acceptance criterion
("logout → token rejected on next call") cannot be verified without it — and a blacklist nothing reads
is untestable. So the **basic `JwtAuthFilter`, a `JwtAuthEntryPoint` (401), and one protected endpoint
`GET /api/me`** are part of Phase 2. Phase 3 is re-scoped to **RBAC (`@PreAuthorize`) + rate limiting**.

---

## 1. Scope

Build all three flows in one slice:

- `POST /api/auth/register` — create user, hash password, issue JWT.
- `POST /api/auth/login` — verify credentials, issue JWT (never reveal whether the email exists).
- `POST /api/auth/logout` — blacklist the current token's `jti` in Redis with TTL = remaining lifetime.

Out of scope (later phases): the JWT validation filter and RBAC (Phase 3), refresh tokens / MFA (V2).

## 2. Architecture & approach

Authentication is performed **manually inside `AuthService`** (look up user → verify password with
`BCryptPasswordEncoder` → issue JWT). We deliberately do **not** stand up Spring Security's
`UserDetailsService` / `AuthenticationManager` yet — the JWT validation filter and RBAC are Phase 3,
and wiring them now would mean half-building infrastructure we'd rework next phase.

`SecurityConfig` (Phase 0) already marks `/api/auth/**` public and is stateless; the only change is
removing the leftover `httpBasic` default.

## 3. Components (new files)

| File | Responsibility |
|---|---|
| `repository/UserRepository` | `findByEmail`, `existsByEmail` |
| `repository/RoleRepository` | `findByName` (assign default `USER` role) |
| `security/JwtService` | Create token (claims: `userId`, `role`, `jti`, `exp`); validate signature + expiry; expose `jti` and remaining TTL |
| `security/TokenBlacklist` | Redis-backed: `blacklist(jti, ttl)`, `isBlacklisted(jti)`; keys `bl:jti:<id>` |
| `service/AuthService` | `register`, `login`, `logout` orchestration |
| `controller/AuthController` | The three endpoints |
| `dto/RegisterRequest`, `dto/LoginRequest`, `dto/AuthResponse` | Java `record`s; bean validation on requests |
| `exception/GlobalExceptionHandler` + exception types | Map errors to clean JSON + correct status codes |
| `config/PasswordConfig` (or in `SecurityConfig`) | `BCryptPasswordEncoder` bean |

`StringRedisTemplate` comes from Spring Boot's Redis auto-configuration; an explicit `RedisConfig`
bean is added only if a customization is required.

## 4. Behavior & data flow

**Register**
1. Validate input (`@Email`, `@NotBlank`, password `@Size(min = 8)`).
2. If `existsByEmail` → `409 Conflict` (signup unavoidably reveals an email is taken).
3. Bcrypt-hash the password.
4. Save `User` with role `USER` (uppercase, matching the V2 seed).
5. Issue JWT → `200 { "token": "..." }`.

**Login**
1. Validate input.
2. Look up by email; verify with `BCryptPasswordEncoder.matches`.
3. On unknown email **or** wrong password → identical generic `401 "Invalid credentials"` (no
   user-existence leak).
4. Issue JWT → `200 { "token": "..." }`.

**Logout**
1. Read `Authorization: Bearer <token>` from the request directly (no filter exists yet).
2. Parse and validate the token; extract `jti` and `exp`.
3. Blacklist `jti` in Redis with TTL = remaining token lifetime → `200`.
4. (Phase 3's filter will consult this blacklist; for Phase 2 we prove rejection by re-checking via
   the blacklist directly.)

## 5. Validation & error handling

`GlobalExceptionHandler` returns structured JSON with the right status:

| Condition | Status | Body message |
|---|---|---|
| Bean-validation failure | `400` | field-level details |
| Unknown email or wrong password | `401` | generic `"Invalid credentials"` |
| Duplicate email on register | `409` | `"Email already registered"` |
| Missing/invalid token on logout | `401` | generic |
| Uncaught | `500` | generic |

**Security discipline:** passwords, hashes, and tokens are never written to logs or returned in
responses (only the freshly minted JWT is returned, by design).

## 6. JWT details

- Algorithm: HMAC-SHA256 (`jjwt`).
- Claims: `sub`/`userId`, `role`, `jti` (random UUID), `iat`, `exp`.
- Secret: `cryptovault.jwt.secret`, env-overridable via `CRYPTOVAULT_JWT_SECRET`, with a
  dev-only default in `application.yml` so the app runs out of the box.
- Lifetime: `cryptovault.jwt.expiration-minutes: 60` (single access token; no refresh in the MVP).

## 7. Config additions (`application.yml`)

```yaml
cryptovault:
  jwt:
    secret: ${CRYPTOVAULT_JWT_SECRET:dev-only-change-me-min-32-bytes-secret-key-1234567890}
    expiration-minutes: 60
```

## 8. Testing (TDD)

- **Unit (JUnit 5 + Mockito):**
  - `JwtService`: create→validate round-trip; rejects expired token; rejects tampered signature;
    `jti` present and unique.
  - `AuthService`: register happy path; duplicate email → conflict; login success; unknown email and
    wrong password both → generic auth failure; logout blacklists the `jti`.
- **Web slice (`@WebMvcTest` + MockMvc):** three endpoints — happy paths, validation failures (400),
  wrong password (generic 401).
- **Manual proof (the plan's "Done when"):** run the real stack —
  register → token; login → token; logout → token's `jti` rejected on reuse; wrong password →
  generic 401.

## 9. Done when

- Register a user → receive a JWT; a row is persisted with a bcrypt hash and role `USER`.
- Login with correct credentials → receive a JWT.
- Logout → the token's `jti` is blacklisted in Redis (rejected on reuse).
- Wrong password (or unknown email) → `401` with a generic message.
- Unit + web-slice tests green.
