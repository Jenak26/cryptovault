# CryptoVault — Detailed Implementation Plan (Spring Boot Edition)

> **Stack:** Java 21 · Spring Boot 3 · Maven · MySQL 8 (Spring Data JPA + Flyway) · Redis · Spring Security · JWT (jjwt) · Bouncy Castle · React + TypeScript
> **Target:** Big traditional banks. **Strategy:** Build the MVP end-to-end first; treat post-quantum as a stretch goal.
> **Companion docs:** `TECH_STACK.md` (what to install), `CryptoVault_Learning_Guide.tex` (concepts taught from zero).

---

## 0. Guiding Principles

1. **Vertical slices, not horizontal.** Get one feature working end-to-end (DB → service → API) before starting the next.
2. **Backend first, frontend last.** Build React only once the API is stable.
3. **Own your schema** with Flyway migrations — never `ddl-auto=update` in anything real.
4. **Crypto discipline:** fresh random nonce every encryption; never log keys or plaintext; never hard-delete a referenced key/algorithm.
5. **Commit small and often.** One logical change per commit.
6. **Every phase ends with a test** proving it works.

---

## 1. Target Architecture

```
React + TypeScript (browser)
        │  HTTPS / JSON
        ▼
Spring Boot application
   ├── Controllers      (REST endpoints, input validation)
   ├── Security filter   (JWT validation + Redis blacklist check)
   ├── Services          (AuthService, VaultService, KeyManager, AuditService)
   ├── CryptoEngine      (CipherStrategy: AES-GCM / ChaCha20-Poly1305)
   └── Repositories      (Spring Data JPA)
        │
        ▼
MySQL 8 (InnoDB, utf8mb4)  ◄──►  Redis (blacklist, rate-limit, OTP)
```

All services run inside one Spring Boot app for the MVP. Microservice splitting is a later decision, not a day-one one.

---

## 2. Project Structure

```
cryptovault/
├── docker-compose.yml            # MySQL + Redis
├── pom.xml                       # Maven dependencies
├── src/main/java/com/cryptovault/
│   ├── CryptoVaultApplication.java
│   ├── config/                   # SecurityConfig, RedisConfig, CryptoConfig
│   ├── controller/               # AuthController, VaultController, AdminController
│   ├── service/                  # AuthService, VaultService, KeyManager, AuditService
│   ├── crypto/                   # CipherStrategy, AesGcmStrategy, ChaCha20Poly1305Strategy, CryptoEngine
│   ├── security/                 # JwtService, JwtAuthFilter, RateLimiter
│   ├── entity/                   # User, Role, CryptoKey, VaultRecord, AuditLog
│   ├── repository/               # *Repository interfaces
│   ├── dto/                      # request/response records
│   └── exception/                # GlobalExceptionHandler
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/             # Flyway: V1__*.sql, V2__*.sql ...
└── frontend/                     # React + TypeScript (built last)
```

---

## 3. Database Schema (MySQL, InnoDB, utf8mb4)

Implemented as JPA entities **and** Flyway migrations. Five tables — translated 1:1 from the original plan.

| Table | Key columns | Notes |
|---|---|---|
| `roles` | `id` PK, `name` UNIQUE | Seed with `admin`, `user`. |
| `users` | `id` CHAR(36) PK, `email` UNIQUE, `password_hash`, `role_id` FK, `is_active` | UUID generated in Java (`uuid4`). |
| `crypto_keys` | `id` PK, `version` UNIQUE, `algorithm`, `status` ENUM(active/retired/revoked) | Raw key material NOT stored here in plaintext. |
| `vault_records` | `id` CHAR(36) PK, `user_id` FK, `encrypted_data` MEDIUMBLOB, `algorithm_used`, `key_version` FK, `created_at` | Stores its own algorithm + key version. |
| `audit_logs` | `id` BIGINT PK, `user_id` (nullable) FK, `action`, `ip_address` VARCHAR(45), `timestamp` | Nullable user for failed logins on unknown emails. |

**Indexes (add now, not retroactively):** `idx_users_email`, `idx_audit_user`, `idx_audit_timestamp`, `idx_vault_user`, `idx_keys_version`.

---

## 4. Implementation Phases

Each phase: **Goal → Tasks → Done when (verification)**. Time estimates are *beginner-realistic* (roughly double the original plan, since Java/Spring is new to you).

### Phase 0 — Environment & Skeleton  ·  ~1 week
**Goal:** A running, empty Spring Boot app talking to MySQL + Redis.
- Install JDK 21, IntelliJ IDEA Community, Docker Desktop, Git.
- Generate the project at [start.spring.io](https://start.spring.io) with: Web, Security, Data JPA, Data Redis, Validation, MySQL Driver, Flyway, Lombok.
- Add dependencies not on Spring Initializr: `jjwt` (api/impl/jackson), `bcprov-jdk18on` (Bouncy Castle).
- Write `docker-compose.yml` (MySQL 8 + Redis 7).
- Configure `application.yml`: MySQL connection (PyMySQL → here it's the MySQL JDBC URL), Redis, Flyway enabled, `ddl-auto=validate`.
- Add a `GET /api/health` endpoint returning `200 {"status":"UP"}`.

**Done when:** `docker compose up` runs MySQL+Redis, app boots with no errors, `/api/health` returns 200.

---

### Phase 1 — Database Schema  ·  ~3–4 days
**Goal:** All five tables exist, versioned and seeded.
- Write JPA entities: `Role`, `User`, `CryptoKey`, `VaultRecord`, `AuditLog`.
- Write Flyway migration `V1__initial_schema.sql` with the full DDL + all indexes (InnoDB, utf8mb4).
- Write `V2__seed_roles.sql` inserting `admin` and `user`.
- Set `ddl-auto=validate` so Hibernate confirms entities match the schema but never edits it.

**Done when:** App starts, Flyway applies V1+V2, `roles` table has two rows, entities validate against the schema.

---

### Phase 2 — Authentication Core  ·  ~1.5 weeks
**Goal:** Register, login, logout working with hashed passwords and JWTs.
- `UserRepository.findByEmail`.
- `AuthService`:
  - **register** → validate input, bcrypt-hash password, insert user (default role `user`), issue JWT.
  - **login** → look up by email, `BCryptPasswordEncoder.matches`, issue JWT. *Never reveal whether the email exists.*
  - **logout** → blacklist the token's `jti` in Redis with TTL = remaining token lifetime.
- `JwtService` (using `jjwt`): create token (claims: `userId`, `role`, `jti`, `exp`), validate signature + expiry.
- `AuthController`: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`.
- DTOs as Java `record`s; bean validation (`@Email`, `@NotBlank`, min length).

**Done when:** Register a user (Postman) → get JWT. Login → get JWT. Logout → token rejected on next call. Wrong password → 401 with a generic message.

> **Phase 2 decisions (locked 2026-06-20):**
> - **Auth wiring:** manual auth inside `AuthService` (no `UserDetailsService`/`AuthenticationManager` yet — JWT filter + RBAC are Phase 3). Remove the leftover `httpBasic` from Phase 0 `SecurityConfig`.
> - **JWT:** HMAC-SHA256; claims `userId`, `role`, `jti`, `exp`. Secret from `cryptovault.jwt.secret` (env-overridable, dev default in `application.yml`). **1-hour** access token; no refresh tokens in the MVP.
> - **Logout:** reads `Authorization` header directly (no filter yet) and blacklists the token's `jti` in Redis with TTL = remaining lifetime (`bl:jti:<id>`).
> - **Errors:** login is **generic 401** for unknown-email *and* wrong-password (no existence leak); register returns **409** on duplicate email (unavoidable for signup). Validation → 400. Passwords/hashes/tokens never logged.
> - **Default role:** `USER` (uppercase, matches the V2 seed).
> - **Design spec:** `docs/superpowers/specs/2026-06-20-phase2-authentication-core-design.md`.

---

### Phase 3 — Security Filter & RBAC  ·  ~1 week
**Goal:** Protected endpoints; admin-only routes enforced.
- `JwtAuthFilter` (extends `OncePerRequestFilter`): read `Authorization: Bearer`, validate, check Redis blacklist, set the Spring Security context.
- `SecurityConfig`: stateless session, public routes (`/api/auth/**`, `/api/health`), everything else authenticated.
- Enable `@PreAuthorize`; mark admin routes `hasRole('ADMIN')`.
- `RateLimiter` (Redis counter) on login: block after N failures per IP/email window.

**Done when:** Calling a protected route without a token → 401. A `user` token on an admin route → 403. An `admin` token → 200. 6th rapid bad login → rate-limited.

---

### Phase 4 — Crypto Engine (crypto-agility core)  ·  ~1.5 weeks
**Goal:** Pluggable encryption selected by config; two algorithms.
- `CipherStrategy` interface: `encrypt(plaintext, key)`, `decrypt(blob, key)`, `name()`.
- `AesGcmStrategy` — AES-256-GCM, **fresh 12-byte random nonce per call**, output = `nonce || ciphertext || tag`.
- `ChaCha20Poly1305Strategy` — via Bouncy Castle, same blob convention.
- `CryptoConfig` — reads active algorithm from `application.yml` / DB.
- `CryptoEngine`: inject all strategies into a `Map<String, CipherStrategy>`; **encrypt** with active algorithm, **decrypt** with the algorithm named in the blob.
- Unit tests: encrypt→decrypt round-trip for both; tampering the ciphertext makes decryption fail (GCM/Poly1305 tag).

**Done when:** Both algorithms round-trip correctly. Switching `algorithm: AES → CHACHA20` in config changes new encryptions, with **no code change**. Tampered ciphertext throws.

> ⚠️ **The one rule:** a unique random nonce for every encryption. Re-read Learning Guide §9.3.

---

### Phase 5 — Key Management & Rotation  ·  ~1 week
**Goal:** Versioned keys; rotate without breaking old data.
- `KeyManager`: holds the active key; key material encrypted at rest under a master secret from an **env var** (never in DB plaintext).
- Seed key `version=1, status=active`.
- `crypto_keys.status`: active / retired / revoked. Exactly one active.
- `POST /api/admin/rotate-key`: generate new version (active), mark previous `retired`.
- Vault writes use the **active** key+version; existing rows keep their original `key_version`.

**Done when:** Store a record (v1). Rotate → v2 active. New records use v2; **old v1 records still decrypt**. v1 key marked retired, never deleted.

---
i w
### Phase 6 — Vault Service  ·  ~1 week
**Goal:** Store and retrieve encrypted records end-to-end.
- `VaultService.store`: encrypt via `CryptoEngine` (active algo+key), save `vault_record` with `encrypted_data`, `algorithm_used`, `key_version`, `user_id`.
- `VaultService.retrieve`: load row, read its `algorithm_used` + `key_version`, fetch that key, decrypt.
- Endpoints: `POST /api/vault/store`, `GET /api/vault/{id}` (owner-only), `DELETE /api/vault/{id}` (**soft-delete** for audit integrity).
- Edge cases: not-owner → 403; missing record → 404; retired-key record still decrypts.

**Done when:** Store a secret, retrieve identical plaintext. Switch active algorithm, store a second secret — **both** still retrieve correctly (each uses its own stored algorithm/version).

---

### Phase 7 — Audit Logging  ·  ~3–4 days
**Goal:** Every security-relevant action is logged.
- `AuditService.log(userId, action, ip)` — `userId` nullable.
- Hook into auth events (`LOGIN_SUCCESS`, `LOGIN_FAIL`, `LOGOUT`, `REGISTER`) and vault events (`VAULT_STORE`, `VAULT_READ`, `VAULT_DELETE`, `KEY_ROTATE`) via an interceptor/aspect.
- `GET /api/admin/audit` — paginated, filterable (by user, action, time range).

**Done when:** Each action writes an audit row. A failed login against an **unknown** email logs a row with `user_id = NULL`.

---

### Phase 8 — Frontend (React + TypeScript)  ·  ~2 weeks
**Goal:** Banking-style UI over the stable API.
- Scaffold with Vite (`react-ts`); Axios client that attaches the JWT; Tailwind (optional).
- Pages: **Landing** (crypto-agility explainer + architecture), **Login/Register**, **Dashboard** (cards: stored records, active algorithm, current key version, recent activity), **Vault** (table: name, algorithm, version, created, view/delete), **Admin** (Recharts: login attempts over time, algorithm usage, key-rotation history).
- Handle 401 → force re-login; clear, minimal error states; no playful copy.

**Done when:** Full flow in a browser: register → login → store a secret → see it in the vault table → admin views audit charts → logout.

---

### Phase 9 — Reassess before V2/V3 (stretch)
Only after the MVP is solid and demoable:
- **V2:** MFA (TOTP / email OTP via Redis), failed-login alerting, background **re-encryption jobs** (idempotent, resumable, row-by-row).
- **V3:** Post-quantum via Bouncy Castle — ML-KEM, ML-DSA, hybrid `X25519 + ML-KEM`. Budget extra time; treat as a stretch goal, not a committed deliverable.

---

## 5. API Surface (MVP)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | public | Liveness check |
| POST | `/api/auth/register` | public | Create user, hash password, issue JWT |
| POST | `/api/auth/login` | public | Verify credentials, issue JWT |
| POST | `/api/auth/logout` | user | Blacklist current JWT in Redis |
| POST | `/api/vault/store` | user | Encrypt + store a record (active algo/key) |
| GET | `/api/vault/{id}` | owner | Retrieve + decrypt by stored algo/key_version |
| DELETE | `/api/vault/{id}` | owner | Soft-delete a record |
| GET | `/api/admin/audit` | admin | Paginated, filterable audit log |
| POST | `/api/admin/rotate-key` | admin | New key version; retire previous |
| POST | `/api/admin/migrate-algorithm` | admin | Kick off re-encryption job (V2+) |

---

## 6. Testing Strategy

- **Unit tests (JUnit 5 + Mockito):** crypto round-trips, tamper-detection, JWT validity/expiry, password hashing, rate limiter.
- **Integration tests (`@SpringBootTest` + Testcontainers):** real MySQL/Redis in containers; full register→login→store→retrieve flow; rotation correctness.
- **Manual (Postman):** every endpoint, plus edge cases (wrong password, expired token, disabled user, not-owner, retired-key retrieval).
- **Security checks:** confirm passwords/keys/plaintext never appear in logs or responses; generic auth error messages.

---

## 7. Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Spring/Java learning curve stalls progress | High | Do a "Spring Boot + MySQL REST API" tutorial *before* Phase 0. Budget 2× time. |
| Docker/MySQL setup friction (Phase 0) | Medium | Use the provided `docker-compose.yml`; don't install MySQL natively. |
| Nonce reuse / crypto mistake | Medium | Fresh random nonce per call; review Learning Guide §9; unit-test tamper detection. |
| Scope creep into V2/V3 too early | High | Hard stop after Phase 8; reassess. Post-quantum is a stretch goal. |
| Key material mishandled | Medium | Master key from env var; never store raw keys in DB plaintext. |

---

## 8. Definition of Done (MVP)

- [ ] `docker compose up` brings up MySQL + Redis; app boots clean.
- [ ] Flyway-managed schema, five tables, roles seeded.
- [ ] Register / login / logout with bcrypt + JWT + Redis blacklist.
- [ ] JWT filter + RBAC (user vs admin) enforced; login rate-limited.
- [ ] Two algorithms behind `CipherStrategy`; active one config-switchable.
- [ ] Store/retrieve encrypted records; old records decrypt after algorithm switch.
- [ ] Key versioning + rotation; old data survives rotation.
- [ ] Full audit trail incl. nullable-user failed logins; admin audit view.
- [ ] React UI: landing, auth, dashboard, vault, admin charts.
- [ ] Unit + integration tests green; README with run instructions.

---

## 9. Suggested First Three Sessions

1. **Session 1:** Install everything (TECH_STACK.md §5), generate the Spring Boot project, write `docker-compose.yml`, get `/api/health` → 200. *(Read Learning Guide §1–4.)*
2. **Session 2:** Entities + Flyway V1/V2, seed roles, app validates schema. *(Read Learning Guide §6.)*
3. **Session 3:** Register + login + JWT issuance. *(Read Learning Guide §7.)*

> Build the MVP (Phases 0–8) before touching V2/V3. A finished MVP is a strong, bank-relevant portfolio piece on its own.
