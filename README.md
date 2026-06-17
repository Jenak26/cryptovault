# CryptoVault

A crypto-agile secrets vault (Spring Boot 3 · Java 21 · MySQL 8 · Redis · JWT). Backend-first MVP targeting bank-grade requirements. Full plan in [`docs/PLAN.md`](docs/PLAN.md).

## Status

- **Phase 0 — Environment & Skeleton: ✅ complete & verified.** App boots clean against live MySQL + Redis; `GET /api/health` returns `200 {"status":"UP","service":"cryptovault"}`. See [`phase-reports/phase0_done.tex`](phase-reports/phase0_done.tex).
- **Phase 1 — Database Schema: 👉 next (this handoff).**

## Prerequisites

- JDK 21 (Eclipse Temurin recommended — the build targets 21 explicitly)
- Docker Desktop (for MySQL + Redis)
- Git. No system Maven needed — the project ships the Maven wrapper (`mvnw`).

## Run it locally

```bash
cd backend
docker compose up -d          # starts MySQL 8 (:3306) + Redis 7 (:6379)
./mvnw spring-boot:run        # boots the app on :8081
# verify:
curl http://localhost:8081/api/health   # -> {"status":"UP","service":"cryptovault"}
```

Stop infra with `docker compose down` (add `-v` to also wipe the MySQL volume).

> Note: the app runs on **port 8081** (8080 was taken on the original dev machine — see `application.yml`). `application.yml` contains a local-only dev DB password (`devpassword`); it is not a secret.

## Your task — Phase 1: Database Schema

Full spec: [`docs/PLAN.md`](docs/PLAN.md) §4 "Phase 1". In short:

- Write the five JPA entities: `Role`, `User`, `CryptoKey`, `VaultRecord`, `AuditLog` (schema table in PLAN §3).
- Write Flyway migrations `V1__initial_schema.sql` (full DDL + all indexes, InnoDB, utf8mb4) and `V2__seed_roles.sql` (seed `admin`, `user`).
- Keep `ddl-auto=validate` — Flyway owns the schema; Hibernate only validates entities against it.

**Done when:** app starts, Flyway applies V1+V2, the `roles` table has two rows, and entities validate against the schema with no errors.

## Workflow

Branch off `main` (e.g. `git checkout -b phase-1-schema`), commit small and often (one logical change per commit), and open a pull request for review. Don't commit directly to `main`.
