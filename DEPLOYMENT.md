# Deployment Guide

This deploys CryptoVault as a live demo on a **genuinely free** stack:

| Piece | Host | Free tier |
|---|---|---|
| Backend (Spring Boot, Docker) | **Render** Web Service | Free (sleeps after ~15 min idle; ~50 s cold start) |
| MySQL 8 | **Aiven** for MySQL | Free plan, no credit card |
| Redis | **Upstash** Redis | Free serverless tier |
| Frontend (React/Vite) | **Vercel** | Free Hobby |

The app is already deploy-ready (`backend/Dockerfile`, host-injected `PORT`, env-driven CORS/DB/Redis/secrets, `VITE_API_BASE_URL`). Total time: ~20 minutes.

> **Why not Railway?** Railway dropped its always-free tier. The stack above stays free. Any host that runs a Docker image + managed MySQL 8 + Redis works — only the env-var wiring differs (Koyeb, Fly.io, Oracle Cloud Always Free, etc.).

---

## 1. Database — Aiven for MySQL (free)

1. Sign up at [aiven.io](https://aiven.io) → **Create service → MySQL → Free plan**.
2. When it's running, open the service overview and note the connection params (host, port, user, password, database name — Aiven uses `defaultdb`). Aiven requires SSL.
3. Keep this tab open; you'll paste these into Render in step 3.

## 2. Redis — Upstash (free)

1. Sign up at [upstash.com](https://upstash.com) → **Create Database → Redis** (pick a region near your Render region).
2. Note the **endpoint host**, **port**, and **password** (Upstash requires TLS).

## 3. Backend — Render (free, Docker)

1. Sign up at [render.com](https://render.com) → **New → Web Service** → connect `Jenak26/cryptovault`.
2. Set **Root Directory = `backend`**, **Runtime = Docker** (Render auto-detects the Dockerfile). Instance type: **Free**.
3. Add **Environment Variables**:

   | Variable | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://<AIVEN_HOST>:<AIVEN_PORT>/defaultdb?sslMode=REQUIRED&serverTimezone=UTC` |
   | `SPRING_DATASOURCE_USERNAME` | Aiven user (e.g. `avnadmin`) |
   | `SPRING_DATASOURCE_PASSWORD` | Aiven password |
   | `SPRING_DATA_REDIS_HOST` | Upstash endpoint host |
   | `SPRING_DATA_REDIS_PORT` | Upstash port |
   | `SPRING_DATA_REDIS_PASSWORD` | Upstash password |
   | `SPRING_DATA_REDIS_SSL_ENABLED` | `true` |
   | `CRYPTOVAULT_MASTER_SECRET` | long random string (`openssl rand -base64 48`) — keep secret |
   | `CRYPTOVAULT_JWT_SECRET` | a different long random string |
   | `CRYPTOVAULT_CORS_ALLOWED_ORIGINS` | your Vercel URL (fill after step 4) |

4. Deploy. Render gives a URL like `https://cryptovault.onrender.com`.
   Verify: `curl <backend-url>/api/health` → `{"status":"UP","service":"cryptovault"}`.

> Flyway runs the migrations automatically on first boot, and `KeyManager` seeds key version 1.
> **Note:** the dev defaults in `application.yml` assume no Redis TLS; Upstash needs TLS, hence
> `SPRING_DATA_REDIS_SSL_ENABLED=true`. (If your chosen Redis host is plaintext, omit it.)

## 4. Frontend — Vercel (free)

1. [vercel.com](https://vercel.com) → **Add New → Project** → import `Jenak26/cryptovault`.
2. **Root Directory = `frontend`** (Vercel detects Vite: build `npm run build`, output `dist`).
3. **Environment Variable** → `VITE_API_BASE_URL` = your Render backend URL (no trailing slash).
4. Deploy, copy the Vercel URL.

## 5. Close the CORS loop

Render → backend → Environment → set `CRYPTOVAULT_CORS_ALLOWED_ORIGINS` to the exact Vercel URL, then redeploy. (Comma-separate multiple origins.)

---

## 6. Smoke test the live demo

1. Open the Vercel URL → **Register** → **Login**.
2. Store a secret in the Vault → confirm it appears, then view (decrypt) it.
3. On the Dashboard, **Enable 2FA**, add the secret to an authenticator app, confirm the code, then log out and back in — login should now ask for the 6-digit code.
4. Log out → confirm the old token is rejected.

### Optional: a demo admin account
Registration defaults to `USER`. To exercise the Admin views, promote a user in the Aiven MySQL console:

```sql
UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE email = 'you@example.com';
```

Log out/in to pick up the new role, then open the Admin page (audit log, key rotation).

---

## Notes
- **Secrets:** values in `application.yml` are dev-only defaults. Always set real
  `CRYPTOVAULT_MASTER_SECRET` and `CRYPTOVAULT_JWT_SECRET` in the host — never reuse the defaults.
- **Master secret is permanent:** changing `CRYPTOVAULT_MASTER_SECRET` after data exists makes the
  stored data keys unrecoverable. Treat it as a fixed, backed-up value.
- **Cold starts:** Render's free instance sleeps when idle; the first request after a nap takes
  ~50 s. Fine for a portfolio demo; mention it if you link the demo in interviews.
- **Truly-free-forever alternative:** an **Oracle Cloud Always Free** VM can run the existing
  `backend/docker-compose.yml` (MySQL + Redis) plus the app, with no sleep — more ops, zero cost.
