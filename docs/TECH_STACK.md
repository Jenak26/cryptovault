# CryptoVault — Final Tech Stack (Big-Bank / Spring Boot Edition)

> Target: big traditional banks (JPMorgan, Goldman Sachs, Morgan Stanley, Barclays, Deutsche Bank).
> Stack chosen to match what bank back-office / core systems actually run on: **Java + Spring Boot**.

---

## 1. Core Stack (non-negotiables)

| Layer | Technology | Purpose |
|---|---|---|
| Language | **Java 21 (LTS)** | The language. Use the current long-term-support version. |
| Framework | **Spring Boot 3.x** | Backend framework — the heart of the app. |
| Build tool | **Maven** | Manages dependencies & builds. (Gradle is the alternative.) |
| Database | **MySQL 8.0+** | Source of truth: users, vault records, key metadata, audit logs. |
| ORM / DB access | **Spring Data JPA + Hibernate** | Talk to MySQL using Java objects instead of raw SQL. |
| DB migrations | **Flyway** | Version-controls the schema (replaces Alembic from the original plan). |
| Cache | **Redis** | JWT blacklist, rate-limit counters, OTP storage (later phases). |

---

## 2. Security & Cryptography

| Purpose | Technology | Phase |
|---|---|---|
| Auth framework | **Spring Security** | MVP |
| Password hashing | `BCryptPasswordEncoder` (built into Spring Security) | MVP |
| JWT tokens | **jjwt** (`io.jsonwebtoken`) | MVP |
| Encryption (AES-256-GCM, ChaCha20-Poly1305) | **Bouncy Castle** (`bcprov-jdk18on`) + Java JCA | Phase 3 |
| Post-quantum (ML-KEM, ML-DSA, hybrid X25519+ML-KEM) | **Bouncy Castle** (built-in PQC support) | Phase 5 (stretch) |

> Note: In Java, Bouncy Castle covers **both** classical and post-quantum crypto — no separate
> liboqs/oqs library needed (unlike the Python plan). One dependency, the whole crypto roadmap.

---

## 3. Frontend (build LAST)

| Layer | Technology |
|---|---|
| Framework | **React + TypeScript** |
| Build tool | **Vite** |
| HTTP client | **Axios** (calls the Spring Boot API) |
| Charts (admin panel) | **Recharts** |
| Styling | **Tailwind CSS** (optional but recommended) |

---

## 4. Dev Environment & Tooling

| Purpose | Tool |
|---|---|
| Run MySQL + Redis locally | **Docker + Docker Compose** (do NOT install MySQL natively) |
| IDE | **IntelliJ IDEA Community Edition** (free; the standard for Java/Spring) |
| API testing | **Postman** (or IntelliJ HTTP client) |
| Version control | **Git + GitHub** |
| Testing | **JUnit 5 + Mockito** (included with Spring Boot) |

---

## 5. What to Install on Day 1 (don't over-install)

Only these go on your machine manually:

1. **JDK 21** — Eclipse Temurin (https://adoptium.net)
2. **IntelliJ IDEA Community Edition** — https://www.jetbrains.com/idea/download
3. **Docker Desktop** — https://www.docker.com/products/docker-desktop (gives MySQL + Redis)
4. **Git** — https://git-scm.com

Everything else (Spring Boot, Hibernate, jjwt, Bouncy Castle...) is pulled in automatically by
Maven via `pom.xml` — you do NOT install those by hand.

---

## 6. Maven Dependencies (for `pom.xml`)

```
spring-boot-starter-web          # REST APIs
spring-boot-starter-security     # authentication / authorization
spring-boot-starter-data-jpa     # MySQL via Hibernate
spring-boot-starter-data-redis   # Redis cache
spring-boot-starter-validation   # input validation
mysql-connector-j                # MySQL driver
flyway-core                      # schema migrations
flyway-mysql                     # MySQL support for Flyway
jjwt-api / jjwt-impl / jjwt-jackson   # JWT handling
bcprov-jdk18on                   # Bouncy Castle (classical + post-quantum crypto)
spring-boot-starter-test         # JUnit 5 + Mockito (default)
```

---

## 7. One-Line Summary

> **Java 21 + Spring Boot 3 + Maven + MySQL 8 (Spring Data JPA/Hibernate + Flyway) + Redis +
> Spring Security + JWT (jjwt) + Bouncy Castle, with a React + TypeScript frontend, developed
> in IntelliJ using Docker for MySQL/Redis.**

---

## 8. Build Order (mapped to phases)

1. **Setup** — JDK, IntelliJ, Docker, Spring Boot skeleton, `docker-compose.yml` (MySQL + Redis), `/health` endpoint returns 200.
2. **Schema** — 5 tables via JPA entities + Flyway migration, seed `roles` (admin/user).
3. **Auth** — register (bcrypt + JWT), login, logout (blacklist JWT in Redis).
4. **RBAC** — Spring Security filter checks role on protected endpoints.
5. **Crypto Engine** — `CipherStrategy` interface; AES-256-GCM + ChaCha20-Poly1305 via Bouncy Castle.
6. **Vault Service** — wire crypto into store/retrieve; pick decryptor from stored algorithm + key_version.
7. **Key Rotation** — key versioning (active/retired/revoked); rotate endpoint.
8. **Audit Logging** — log user_id, action, ip, timestamp on every auth/vault action.
9. **Frontend** — React: login, dashboard, vault table, admin charts.
10. **Reassess** — MFA, background re-encryption, post-quantum = stretch goals.
