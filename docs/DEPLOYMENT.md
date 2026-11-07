# Deployment

> **Current state:** the repository is production-*ready* in structure
> (config validation, health probes, CI, Dockerfiles) but has **not** been
> deployed to a production environment yet. This document describes the
> intended topology and the exact steps and checks to run when doing so.

## Topology (target)

```
                 ┌────────────────────────── CDN / edge (Vercel)
 Users ─HTTPS──► │  Next.js frontend (static + SSR)      frontend domain
                 └───────────────┬──────────────────────┘
                                 │ HTTPS (CORS: exact origin)
                 ┌───────────────▼──────────────────────┐
                 │  Spring Boot backend (Render)        │ backend domain
                 │  /api/v1/**  /actuator/health        │
                 └──────┬───────────────────────┬───────┘
                        │ private network       │
              ┌─────────▼────────┐     ┌────────▼───────┐
              │ Managed Postgres │     │ Managed Redis  │
              │ 17 (Flyway on)   │     │ (throttling)   │
              └──────────────────┘     └────────────────┘
```

Development `docker-compose.yml` is **not** for production: it publishes
database ports and uses dev credentials. Production runs Postgres/Redis as
managed services with private networking and no published ports.

## Build artifacts

| Component | Build | Output |
| --- | --- | --- |
| Backend | `./mvnw -B clean package` (skips ITs: `-DskipITs` if desired) | `bizgrow-backend-*.jar` — run with `java -jar` |
| Frontend | `npm ci && npm run build` | Next production server (`npm start`) or static export per route |
| Docker | `infrastructure/docker/{backend,frontend}.Dockerfile` | Multi-stage images referenced by compose |

CI (`.github/workflows/`) runs the full gates on every push/PR:
backend `./mvnw -B verify` (unit + Testcontainers ITs), frontend
`npm ci` → typecheck → lint → test → build.

## Environment (production)

All values come from the platform's secret store — nothing has a working
default in base `application.yml`:

| Variable | Production guidance |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` (no dev defaults, no `expose-dev-token`) |
| `DATABASE_URL/USERNAME/PASSWORD` | Managed Postgres, TLS JDBC params (`?sslmode=require`) |
| `REDIS_HOST/PORT/PASSWORD` | Managed Redis with auth + TLS |
| `JWT_SECRET` | ≥ 64 random chars, generated once, stored as a secret |
| `CORS_ALLOWED_ORIGINS` | Exactly the frontend origin, e.g. `https://app.yemomtech.et` |
| `FRONTEND_URL` | Same origin, used in invitation links |
| `COOKIE_SAME_SITE=None`, `COOKIE_SECURE=true` | Required for cross-site (Vercel ↔ Render) cookies |
| `JWT_ACCESS_EXPIRATION_SECONDS` / `JWT_REFRESH_EXPIRATION_DAYS` | `900` / `30` defaults are sane |
| `APP_PORT` | `8080` (or the platform's `$PORT`) |

Never commit real values; `.env` is gitignored and `NEXT_PUBLIC_*` values
are public by design (no secrets there).

## Startup validation (what the app refuses to run with)

`StartupValidator` + profile rules fail fast on:

- missing/short `JWT_SECRET`;
- dev-grade secret patterns in production;
- `app.security.invitation.expose-dev-token=true` outside dev/test;
- wildcard (`*`) combined with credentials in CORS origins;
- unreachable required properties (unresolved `${…}` placeholders).

## Database migration on deploy

- Flyway runs automatically at startup, before the app serves traffic
  (`validate-migration-naming: true`).
- Migrations are **forward-only** — never edit an applied file; add
  `Vn+1__description.sql`. CI validates the whole chain on a fresh DB.
- For risky changes: expand/contract pattern (add column → backfill →
  switch reads → drop old column in a later release).
- `ddl-auto: validate` guarantees entities match the migrated schema; a
  mismatch aborts startup instead of corrupting data.

## Health & monitoring

| Endpoint | Use |
| --- | --- |
| `/actuator/health/liveness` | Container/orchestrator liveness probe |
| `/actuator/health/readiness` | Readiness (DB + Redis reachable) |
| `/actuator/health` | Aggregated health; details hidden (`show-details: never`) |

Only `health` and `info` are exposed. Logs are structured around the
`traceId` from `RequestTraceFilter` (also returned to clients in error
envelopes) — grep a user-reported `traceId` to find the full request story.

## Security checklist before going live

- [ ] `SPRING_PROFILES_ACTIVE=prod`; dev profile cannot activate.
- [ ] `JWT_SECRET` from the secret store, ≥ 32 bytes entropy.
- [ ] `CORS_ALLOWED_ORIGINS` = exact frontend origin(s), no wildcard.
- [ ] `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=None` for cross-site; verify
      the refresh cookie flags in browser dev tools.
- [ ] `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`.
- [ ] TLS everywhere (platform terminator is fine); DB TLS required.
- [ ] Postgres/Redis not published to the internet; strong passwords.
- [ ] Rate limiting/`LOGIN_*` values reviewed; Redis failure mode
      (fail-open) understood and accepted (ADR-0004).
- [ ] `actuator` exposure limited to `health,info` (already default).
- [ ] Backups: Postgres PITR/automated snapshots verified with a restore
      drill.
- [ ] `.env` never deployed; secrets injected by the platform.

## Rollback

1. **App**: redeploy the previous image/jar — schema is backward-compatible
   by expand/contract discipline, so rolling the app back never requires
   rolling back migrations.
2. **Bad migration**: restore from backup + redeploy previous release;
   treat as an incident (migrations are append-only, no `down` scripts by
   design).
3. **Frontend/backend mismatch**: frontend guards are additive (unknown
   fields ignored); prefer deploying backend first, then frontend.

## Invitation delivery in production

`InvitationDelivery` implementations:

- **`DevInvitationDelivery`** — returns the raw token in the API response
  and logs `[DEV] Invitation issued …`; enabled only by
  `expose-dev-token` (dev/test profiles, refused in prod).
- **`LoggingInvitationDelivery`** — the production default: logs that an
  invitation was issued **without** the token.

Real e-mail delivery (SES/SendGrid) is a ROADMAP item; until it lands,
production invitation links must be conveyed out-of-band by an admin (the
frontend's accept form takes the token).
