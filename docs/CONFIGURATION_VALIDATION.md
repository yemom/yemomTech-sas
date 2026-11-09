# YemomBizGrow — Configuration & Environment Validation

This document defines the canonical environment variable contract for the YemomBizGrow SaaS platform across local development, Docker Compose, and production environments (e.g., Render, Kubernetes, AWS).

---

## 1. Unified Environment Variable Contract

| Environment Variable | Canonical Purpose | Default (Dev/Local) | Production Requirement |
|----------------------|-------------------|---------------------|------------------------|
| `APP_ENV` | Environment identifier | `development` | `production` |
| `APP_PORT` / `SERVER_PORT` | Backend HTTP port | `8080` (or `8082` local dev) | Required / Assigned by host |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` | `prod` |
| `DATABASE_URL` | PostgreSQL JDBC Connection URL | `jdbc:postgresql://127.0.0.1:5632/YemomBizGrow` | **Mandatory** |
| `DATABASE_USERNAME` | PostgreSQL User | `YemomBizGrow_user` | **Mandatory** |
| `DATABASE_PASSWORD` | PostgreSQL Password | Dev password | **Mandatory** |
| `REDIS_HOST` | Redis hostname | `127.0.0.1` (or `redis` in compose) | **Mandatory** |
| `REDIS_PORT` | Redis port | `6637` (or `6379` in compose) | `6379` |
| `REDIS_PASSWORD` | Redis auth password | `""` | Optional / Provider-dependent |
| `JWT_SECRET` | 256-bit secret for signing HMAC JWTs | Dev default (rejected in prod) | **Mandatory** (>= 64 chars) |
| `JWT_ACCESS_EXPIRATION_SECONDS` | Access token lifespan | `900` (15 minutes) | `900` |
| `JWT_REFRESH_EXPIRATION_DAYS` | Refresh token lifespan | `30` | `30` |
| `COOKIE_SAME_SITE` | Refresh cookie SameSite policy | `Lax` (same-origin local) | `None` (cross-origin HTTPS) |
| `COOKIE_SECURE` | Secure cookie flag | `false` | `true` |
| `CORS_ALLOWED_ORIGINS` | Permitted browser origins | `http://localhost:3000,http://localhost:3001` | **Mandatory** (Exact domain) |
| `FRONTEND_URL` | User-facing web application URL | `http://localhost:3000` | **Mandatory** (e.g., https://app.yemombizgrow.com) |
| `ENCRYPTION_KEY` | AES-256-GCM 32-byte Base64 key | Dev key in `application-dev.yml` | **Mandatory** (Refuses startup if missing/dev) |
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID | `""` (fails closed on connect) | **Mandatory** for Google integrations |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 Client Secret | `""` | **Mandatory** for Google integrations |
| `GOOGLE_REDIRECT_URI` | Google OAuth2 Callback URL | `http://localhost:3000/workspace/analytics/callback` | **Mandatory** matching Google Console |
| `CHAPA_SECRET_KEY` | Chapa Payment API secret key | `""` (mock in dev/test) | **Mandatory** for live payments |
| `CHAPA_WEBHOOK_SECRET` | Chapa webhook HMAC signature secret | `""` (mock in dev/test) | **Mandatory** for webhook verification |
| `OPENAI_API_KEY` | OpenAI API key for intelligent chatbot | `""` (falls back to OfflineAiProvider) | Optional (Offline fallback active if absent) |
| `OPENAI_MODEL` | OpenAI Model name | `gpt-4o-mini` | `gpt-4o-mini` |
| `MAIL_HOST` / `SPRING_MAIL_HOST` | SMTP server host | `""` (logs warning, graceful skip) | **Mandatory** for live email delivery |
| `MAIL_PORT` / `SPRING_MAIL_PORT` | SMTP server port | `587` | `587` or `465` |
| `MAIL_USERNAME` / `SPRING_MAIL_USERNAME` | SMTP username | `""` | **Mandatory** for live email delivery |
| `MAIL_PASSWORD` / `SPRING_MAIL_PASSWORD` | SMTP password | `""` | **Mandatory** for live email delivery |
| `NEXT_PUBLIC_API_BASE_URL` | Frontend browser API endpoint | `http://localhost:8080/api/v1` | **Mandatory** build argument |

---

## 2. Startup Validation & Fail-Closed Guards

The backend includes a strict `StartupValidator` (`StartupValidator.java`) that executes on `ApplicationReadyEvent`:
1. **Production Profile Guard:**
   - If `SPRING_PROFILES_ACTIVE=prod`, the application **aborts startup** with `IllegalStateException` if:
     - `ENCRYPTION_KEY` is missing, blank, shorter than 32 decoded bytes, or equals the known development default key.
     - `JWT_SECRET` equals the development placeholder or is under 32 bytes of entropy.
     - `CORS_ALLOWED_ORIGINS` contains `*` wildcard or is blank.
     - `CHAPA_SECRET_KEY` is set to `mock` or is blank when payments are initiated.
     - `invitation.expose-dev-token` is true.
2. **Graceful Optional Integration Degradation:**
   - In `dev` / `test` profiles:
     - If `OPENAI_API_KEY` is not provided, `OfflineAiProvider` provides deterministic, org-grounded business rules without network calls.
     - If `MAIL_HOST` is not provided, `DefaultEmailDelivery` logs a warning and does not block account registration or user workflows.
     - If `CHAPA_SECRET_KEY` is not provided in dev/test, simulated sandbox URLs are generated; in `prod`, it strictly fails closed.
