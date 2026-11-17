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
| Backend | `./mvnw -B clean package` (skips ITs: `-DskipITs` if desired) | `YemomBizGrow-backend-*.jar` — run with `java -jar` |
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
| `ENCRYPTION_KEY` | Base64-encoded 32-byte AES key; `openssl rand -base64 32` |
| `GOOGLE_CLIENT_ID` | Google Cloud OAuth 2.0 client ID |
| `GOOGLE_CLIENT_SECRET` | Google Cloud OAuth 2.0 client secret |
| `GOOGLE_REDIRECT_URI` | OAuth callback — `https://app.yourdomain.com/workspace/analytics/callback` |
| `MAIL_HOST / MAIL_PORT` | SMTP relay hostname and port (e.g. SendGrid `smtp.sendgrid.net:587`) |
| `MAIL_USERNAME / MAIL_PASSWORD` | SMTP credentials |
| `MAIL_FROM / MAIL_FROM_NAME` | From address displayed in invitation and verification emails |
| `SPRING_PROFILES_ACTIVE` | `prod` (no dev defaults, no `expose-dev-token`) |
| `DATABASE_URL/USERNAME/PASSWORD` | Managed Postgres, TLS JDBC params (`?sslmode=require`) |
| `REDIS_HOST/PORT/PASSWORD` | Managed Redis with auth + TLS |
| `JWT_SECRET` | ≥ 64 random chars, generated once, stored as a secret |
| `CORS_ALLOWED_ORIGINS` | Exactly the frontend origin, e.g. `https://app.yemomtech.et` |
| `FRONTEND_URL` | Same origin, used in invitation links |
| `COOKIE_SAME_SITE=None`, `COOKIE_SECURE=true` | Required for cross-site (Vercel ↔ Render) cookies |
| `JWT_ACCESS_EXPIRATION_SECONDS` / `JWT_REFRESH_EXPIRATION_DAYS` | `900` / `30` defaults are sane |
| `APP_PORT` | `8080` (or the platform's `$PORT`) |

### Frontend build requirement: `NEXT_PUBLIC_API_BASE_URL`

`NEXT_PUBLIC_API_BASE_URL` (e.g. `https://api.yourdomain.com/api/v1`) is a
**build-time** variable: Next.js bakes it into the browser bundle when
`next build` runs. Setting it in the hosting environment alone does **not**
change an already-built frontend — after changing it, rebuild and redeploy.

The build refuses to proceed without it (no silent localhost fallback):

```bash
# production build — fails with an explicit error if the variable is unset
NEXT_PUBLIC_API_BASE_URL="https://api.yourdomain.com/api/v1" npm run build

# production Docker image — the same rule, enforced before the build runs
docker build -f infrastructure/docker/frontend.Dockerfile \
  --build-arg NEXT_PUBLIC_API_BASE_URL="https://api.yourdomain.com/api/v1" .

# CI publishes the image only when the repository variable
# NEXT_PUBLIC_API_BASE_URL is configured (Settings → Secrets and variables → Variables)
```

In CI, the frontend image job passes `vars.NEXT_PUBLIC_API_BASE_URL`; if the
repository variable is unset, the Docker build fails with an explicit
message instead of publishing an image that talks to `localhost`. The local
compose stack passes `http://localhost:8080/api/v1` itself, so local
development is unaffected.

Verify what was actually baked into a build:

```bash
grep -rl "api.yourdomain.com/api/v1" .next/static/chunks/ | head
```

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

## Live provider verification (staging)

All automated tests prove that the integration code is **correct against
controlled responses**. None of them proves that a real provider accepts a
real request. That can only be done in staging with real credentials, and it
is a release checklist, not a unit test.

`infrastructure/scripts/verify-providers.mjs` automates the parts that can be
automated, and reports each provider as `PASS`, `SKIP`, `PARTIAL` or `FAIL`.
It never sends email, never initializes a payment and never modifies state —
checks that require a human to complete the flow are marked `PARTIAL`.

```bash
OPENAI_API_KEY=sk-... CHAPA_SECRET_KEY=... CHAPA_TEST_TXREF=... \
  GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... GOOGLE_REDIRECT_URI=... \
  SPRING_MAIL_HOST=... SPRING_MAIL_PORT=587 SPRING_MAIL_USERNAME=... SPRING_MAIL_PASSWORD=... \
  node infrastructure/scripts/verify-providers.mjs
```

**Current status: the staging provider checks have NOT been run.**
No staging credentials are available in this environment, so every check
reports `SKIP`. Do not treat the absence of a failure as a pass — it means
nothing has been checked yet.

### Manual staging checklist

For each provider, complete **all** of these before production approval.
Record the results (date, operator, outcome) in the release ticket.

**OpenAI (AI provider)**
1. Set `OPENAI_API_KEY` in the staging environment.
2. Run the harness — it must report PASS.
3. In the staging UI, open AI Insights and trigger "Generate fresh insights";
   confirm the reply appears and is grounded in the organization's own data.
4. Confirm an abnormally long chat conversation still answers within the
   timeout (the provider has a 15 s deadline) and that the fallback path is
   exercised when the key is removed.

**Chapa (billing)**
1. Use the Chapa **sandbox** key and a test transaction in staging.
2. Run the harness with `CHAPA_TEST_TXREF=<sandbox txRef>` — it must report
   PASS, i.e. Chapa accepted the secret key and returned the transaction.
3. Complete a real sandbox checkout end to end in the staging UI.
4. Verify the transaction appears as `SUCCESS` in PostgreSQL, the webhook
   fired, and the subscription was activated once — then replay the webhook
   and confirm it is **not** applied a second time.
5. Verify amount/currency mismatches are rejected and produce `FAILED` with a
   reason.

**Google OAuth (analytics)**
1. Set `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` /
   `GOOGLE_REDIRECT_URI` in staging, with the redirect URI whitelisted in
   the Google Cloud console.
2. Run the harness — client id format and redirect URI must be valid.
3. Complete the OAuth flow in the staging UI: connect a Google account,
   confirm the access token is stored encrypted, the scopes are correct, and
   the dashboard shows real analytics data for that account.
4. Revoke the token in Google's console, reload the dashboard and confirm the
   application detects the revocation and prompts to reconnect — not a crash.

**SMTP (email)**
1. Set `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` /
   `SPRING_MAIL_PASSWORD` in staging.
2. Run the harness — the SMTP server must be reachable from the backend and
   the credentials accepted for authenticated SMTP submission.
3. Send a real test email in staging (register a new user and verify the
   address, or use the password-reset flow) and confirm delivery.
4. Confirm the message is not marked as spam and links point at the staging
   frontend, not localhost.

### What "done" looks like

`verify-providers.mjs` exits 0, and the four manual checklists above are
completed and recorded. Until both happen, the status remains
**READY FOR STAGING — PRODUCTION BLOCKED**.

---

## Dependency vulnerability policy

The pipeline **fails** on a known vulnerability — it does not merely warn.
The policy lives in one place and is enforced by code, not by convention.

| Scope | Blocking threshold | Tool |
| --- | --- | --- |
| npm production dependencies | **HIGH** and CRITICAL | `infrastructure/scripts/audit-policy.mjs --scope=prod --severity=high` |
| npm development dependencies | **CRITICAL** only | `infrastructure/scripts/audit-policy.mjs --scope=all --severity=critical` |
| Maven dependencies | **HIGH** and above (CVSS ≥ 7) | OWASP Dependency-Check, `fail-on-severity: HIGH` |
| Pull requests | **HIGH** and above | `actions/dependency-review-action` (GitHub Advisory Database) |

Running the gates locally:

```bash
node infrastructure/scripts/audit-policy.mjs --scope=prod --severity=high
node infrastructure/scripts/audit-policy.mjs --scope=all --severity=critical
node infrastructure/scripts/audit-policy.test.mjs     # proves the gate fails
```

### Exceptions

An advisory may only be excepted with **a documented reason and an expiry
date**, recorded in `.github/dependency-exceptions.json`:

```json
{
  "1096522": {
    "reason": "Why this does not affect us.",
    "expires": "2026-12-31"
  }
}
```

The gate treats an exception with no reason, no expiry, or a past expiry as
**blocking** — an expired exception cannot silently re-approve a dependency.
Prefer an explicit `overrides` entry in `frontend/package.json` (or a pinned
upgrade) over a blanket exception.

`audit-policy.test.mjs` is a self-test that feeds synthetic advisory reports
through the same evaluation logic and asserts the exit codes, so the gate
itself cannot rot into a no-op.

### What the gate cannot see

- OWASP Dependency-Check downloads the NVD feed; without `secrets.NVD_API_KEY`
  the scan is slower and may be based on the last cached feed. **Set
  `NVD_API_KEY` in the repository secrets** for complete coverage.
- The gate evaluates *declared* dependencies. It does not detect a
  compromised upstream build or a dependency used in a way the advisory does
  not describe — that is what the staging provider checks (below) are for.

---

## Load testing

`infrastructure/scripts/load-test.mjs` is a dependency-free load harness used
for this stack. Example:

```bash
node infrastructure/scripts/load-test.mjs \
  --url=http://localhost:8080/actuator/health/readiness \
  --requests=500 --concurrency=25
```

Measured locally (development profile, single instance, 500 requests,
concurrency 25):

```
throughput    : 354.6 req/s
latency mean  : 53.3 ms
latency p50   : 37.7 ms
latency p95   : 135.9 ms
latency p99   : 371.8 ms
failed        : 0 (0.00%)
```

The frontend (Next.js standalone) serves 100 requests at concurrency 10
without errors (p50 ≈ 74 ms). These numbers characterise the **development**
stack only and are a starting baseline, not a capacity claim — the
production sizing question is answered by load-testing the staging stack with
realistic request mixes (see "Remaining work" in
`docs/PRODUCTION_STABILIZATION_REPORT.md`).

## Monitoring & alerting

### What the application emits

| Signal | Where | Purpose |
| --- | --- | --- |
| Correlation `traceId` | `RequestTraceFilter` (and error envelopes) | Find every log line for one request |
| `bizgrow.security.controls.degraded` counter | `/actuator/metrics` (exposed in dev/test) | Tells you a security control is running degraded |
| `SECURITY CONTROL DEGRADED` log marker | application logs | Human-readable cause (Redis unreachable, etc.) |
| `/actuator/health/readiness` | uptime probe | DB + Redis reachability |
| `/actuator/health/liveness` | liveness probe | Process is alive |
| `/actuator/health` | aggregate | Optional components (e.g. mail) included |

### Degraded security controls — rules to add

A degradation means the application stayed up but a security control weakened
(in-process, per-instance, conservative limit). Alert on this rather than
only logging it:

```promql
# Fires the moment any security control falls back to its in-process limiter.
increase(bizgrow_security_controls_degraded_total[5m]) > 0
```

With a `control` label, alert per control:

```promql
increase(bizgrow_security_controls_degraded_total{control="login-throttle"}[5m]) > 0
increase(bizgrow_security_controls_degraded_total{control="ai-generation-guard"}[5m]) > 0
increase(bizgrow_security_controls_degraded_total{control="lead-rate-limit"}[5m]) > 0
```

Treat the first occurrence in 15 minutes as a warning and a continuing
occurrence as a page — a sustained degradation means Redis (or the network in
front of it) is down.

### Operator runbook

1. **Check Redis**: `docker compose ps redis`, `redis-cli -p 6637 ping`.
2. **Check connectivity**: from the backend container, `redis-cli -h redis -p 6379 ping`.
3. **If Redis is down**: restart it, or point `REDIS_HOST`/`REDIS_PORT` at the
   backup instance. The degraded in-process limit keeps the system
   brute-force-limited in the meantime (at most 5 login attempts / 15 min per
   account / 20 per IP, per instance).
4. **If Redis is up**: confirm the trendline returns to zero — the rate
   counter keeps counting past events, the log shows the cause and the
   recovery has already happened automatically.

### Production endpoint exposure

The `metrics` endpoint is exposed in the `dev`/`test` profiles for local
inspection. **Production keeps `health,info` only.** In every profile the
endpoint requires a bearer token — it is not in the anonymous allowlist. An
unauthenticated scrape returns 401. Scrape it with a monitoring token or from
an internal network protected by an authenticating proxy.

The full chain was verified on this stack:

```
$ curl -H "Authorization: Bearer <token>" \
       http://localhost:8091/actuator/metrics/bizgrow.security.controls.degraded

{"name":"bizgrow.security.controls.degraded",
 "description":"How often a security control fell back to its in-process limiter",
 "measurements":[{"statistic":"COUNT","value":12.0}],
 "availableTags":[{"tag":"control","values":["login-throttle"]}]}

$ curl http://localhost:8091/actuator/metrics/bizgrow.security.controls.degraded   # no token
401 Unauthorized
```

### Multi-instance Redis failure drill

`infrastructure/scripts/redis-failure-drill.sh` proves the degraded mode and
the recovery across two backend instances that share PostgreSQL and Redis:

```
$ bash infrastructure/scripts/redis-failure-drill.sh
...
  attempt 6 -> HTTP 429
second instance correctly throttled the brute-force attempt
primary (8080) readiness: HTTP 200
secondary (8091) readiness: HTTP 200
DRILL PASSED
```

What the drill establishes:

1. a second instance runs side by side with the first on the same database
   and the same Redis;
2. when Redis goes down, **both** instances stay up (readiness 200/200);
3. the login throttle degrades to its in-process limiter on **both**
   instances — five wrong passwords are refused with 401, the sixth with
   429, on the instance that was brute-forced;
4. Redis is restarted and the primary instance recovers by itself;
5. the degraded-control counter (`bizgrow.security.controls.degraded`,
   tag `control=login-throttle`) recorded the twelve throttled attempts;
   readable with a token, 401 without.

Prerequisites: Docker, the compose stack, and a free host port (the drill
publishes the second instance on 8091; change `SECONDARY_URL` if that port is
taken). The drill registers a throwaway account and performs failed logins
against it — never run it against production.

---

## Database backup and restore

`infrastructure/scripts/postgres-backup.sh` implements the whole procedure.

```bash
# Create a timestamped dump in ./backups (custom format, compressed)
bash infrastructure/scripts/postgres-backup.sh backup

# Restore a dump into a DIFFERENT database (the source is protected)
bash infrastructure/scripts/postgres-backup.sh restore <file.dump> <target-db>

# Full drill: backup -> restore into an isolated scratch database -> compare
# every table -> drop the scratch database again
bash infrastructure/scripts/postgres-backup.sh verify
```

The `verify` drill is what CI runs
(`.github/workflows/ci.yml`, "Verify the database backup and restore
procedure"), and it checks four things:

1. **The safety guard** — restoring over the database the dump came from is
   refused (the rule is checked in isolation, so no database is touched).
2. **Dump integrity** — the archive is non-empty and `pg_restore --list`
   finds its table data.
3. **Data fidelity** — every table's row count in the restored copy equals
   the source.
4. **Schema usability** — the restored database carries a consistent Flyway
   history (`flyway_schema_history` with successful migrations), so the
   application can start against it without re-running migrations.

The scratch database is dropped again at the end, so the drill leaves the
application database untouched. **Never point `restore` at a production
database**: the guard only protects the database named in `SOURCE_DB`.

For a managed PostgreSQL (Render, RDS, Cloud SQL) use the provider's own
snapshot/PITR facility and run the same drill against a restored copy — the
script's `PG_CONTAINER`/`PG_HOST`/`PG_PORT` variables exist for that.

### Recovery after an application failure

1. Stop the application (the database is unaffected).
2. Restore the most recent dump into an empty database and point the
   application at it.
3. Start the application; Flyway finds the restored `flyway_schema_history`
   and applies only the migrations that are newer than the dump.
4. Verify with `/actuator/health/readiness` and the dashboard counts.

Redis holds no durable state (login throttling and the AI generation lock
only), so it needs no backup — see the degradation notes in ADR-0004.

## Security checklist before going live

- [ ] `SPRING_PROFILES_ACTIVE=prod`; dev profile cannot activate.
- [ ] `JWT_SECRET` from the secret store, ≥ 32 bytes entropy.
- [ ] `CORS_ALLOWED_ORIGINS` = exact frontend origin(s), no wildcard.
- [ ] `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=None` for cross-site; verify
      the refresh cookie flags in browser dev tools.
- [ ] `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`
      (already the default in the `prod` profile; startup refuses to boot
      with them enabled).
- [ ] TLS everywhere (platform terminator is fine); DB TLS required.
- [ ] Postgres/Redis not published to the internet; strong passwords.
- [ ] Rate limiting/`LOGIN_*` values reviewed; Redis failure mode
      understood — the login throttle degrades to a bounded in-process limit
      and logs `SECURITY CONTROL DEGRADED` (alert on that marker).
- [ ] `actuator` exposure limited to `health,info` (already default).
- [ ] Backups: Postgres PITR/automated snapshots verified with a restore
      drill (`postgres-backup.sh verify`).
- [ ] Frontend built with `NEXT_PUBLIC_API_BASE_URL` set to the public
      backend URL — verified by grepping the built bundle (see "Frontend
      build requirement" above); a localhost URL in a production bundle is a
      release blocker.
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
