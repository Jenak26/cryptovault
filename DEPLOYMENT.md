# Deployment Guide

This deploys CryptoVault as a live demo: **backend + MySQL + Redis on [Railway](https://railway.app)**
and the **frontend on [Vercel](https://vercel.com)**. Both have free tiers and deploy straight from
this GitHub repo. Total time: ~15 minutes.

The app is already deploy-ready:
- `backend/Dockerfile` — multi-stage build, runs as non-root.
- `server.port` honours the host-injected `PORT`.
- CORS origins, DB, Redis, and secrets are all environment-driven (no code changes to deploy).
- Frontend API base URL is set at build time via `VITE_API_BASE_URL`.

---

## 1. Backend + databases on Railway

1. **New Project → Deploy from GitHub repo** → pick `Jenak26/cryptovault`.
2. In the service settings, set **Root Directory = `backend`** (Railway auto-detects the Dockerfile).
3. **Add a MySQL database**: *New → Database → MySQL*. **Add Redis**: *New → Database → Redis*.
4. On the **backend service → Variables**, add (use Railway's variable references for the DB values):

   | Variable | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
   | `SPRING_DATASOURCE_USERNAME` | `${{MySQL.MYSQLUSER}}` |
   | `SPRING_DATASOURCE_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
   | `SPRING_DATA_REDIS_HOST` | `${{Redis.REDISHOST}}` |
   | `SPRING_DATA_REDIS_PORT` | `${{Redis.REDISPORT}}` |
   | `CRYPTOVAULT_MASTER_SECRET` | a long random string (≥ 32 bytes) — generate one, keep it secret |
   | `CRYPTOVAULT_JWT_SECRET` | a different long random string (≥ 32 bytes) |
   | `CRYPTOVAULT_CORS_ALLOWED_ORIGINS` | your Vercel URL (fill in after step 2, e.g. `https://cryptovault.vercel.app`) |

   > Generate secrets locally: `openssl rand -base64 48`

5. Deploy. When it's up, Railway gives the service a public URL (e.g. `https://cryptovault-production.up.railway.app`).
   Verify: `curl <backend-url>/api/health` → `{"status":"UP","service":"cryptovault"}`.

> Flyway runs the migrations automatically on first boot, and `KeyManager` seeds key version 1.

---

## 2. Frontend on Vercel

1. **Add New → Project** → import `Jenak26/cryptovault`.
2. Set **Root Directory = `frontend`** (Vercel detects Vite: build `npm run build`, output `dist`).
3. **Environment Variables** → add `VITE_API_BASE_URL` = your Railway backend URL (no trailing slash).
4. Deploy. Copy the resulting Vercel URL.

---

## 3. Close the CORS loop

Go back to Railway → backend → Variables and set `CRYPTOVAULT_CORS_ALLOWED_ORIGINS` to the exact
Vercel URL from step 2, then redeploy the backend. (Multiple origins are comma-separated.)

---

## 4. Smoke test the live demo

1. Open the Vercel URL → **Register** an account → **Login**.
2. Store a secret in the Vault → confirm it appears, then view (decrypt) it.
3. Log out → confirm the old token is rejected.

### Optional: a demo admin account
The default role for registration is `USER`, so admin views need an `ADMIN` user. Easiest path:
register a user, then in the Railway MySQL console point that user's `role_id` at the `ADMIN` role:

```sql
UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE email = 'you@example.com';
```

Log out and back in to pick up the new role, then open the Admin page (audit log, key rotation).

---

## Notes
- **Secrets:** the values in `application.yml` are dev-only defaults. Always set real
  `CRYPTOVAULT_MASTER_SECRET` and `CRYPTOVAULT_JWT_SECRET` in the host — never reuse the defaults.
- **Master secret is permanent:** if you change `CRYPTOVAULT_MASTER_SECRET` after data exists, the
  stored data keys can no longer be unwrapped. Treat it as a fixed, backed-up value.
- **Alternatives:** any host that runs a Docker image + managed MySQL 8 + Redis 7 works
  (Render, Fly.io, AWS). Only the env-var wiring differs.
