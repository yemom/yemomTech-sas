# YemomBizGrow — Final Staging Deployment & Production Readiness Audit

**Date:** 17 November 2026
**Commits audited:** `fd62e64` (authentication hardening), `b567869` (docs)
**Audit method:** every check below was executed against the deployed
artifacts (Docker images built by this repository, running under Docker
Compose on the audit machine) with raw HTTP (curl), a real browser
(Playwright), direct database inspection (psql), and generated reports. No
result in the "Verified" tables is an assumption.

**Honest scope statement:** this machine cannot provide a *public staging
environment* (a public HTTPS domain) or *live provider credentials*. Where
that matters, the finding says so explicitly and is classified as an
external release blocker, not as a verified pass.

---

## 1. Deployment verification

| Check | Result | Method |
| --- | --- | --- |
| Frontend container serves the configured API URL | **PASS** | Every served `_next/static/chunks/*.js` referenced by `/login` was fetched and grepped: contains the configured backend URL, contains **no** stale `localhost:8080` |
| Images built with the required-URL Dockerfile | **PASS** | `docker compose build frontend` runs the guard (visible in the build log) |
| Image build without `NEXT_PUBLIC_API_BASE_URL` | **FAILS BY DESIGN** | `docker build` exits 1 with an explicit message before `npm run build` |
| Production build without the variable | **FAILS BY DESIGN** | `next build` exits 1: "NEXT_PUBLIC_API_BASE_URL is not set…" |
| Compose stack health | **PASS** | backend / frontend / postgres / redis all `healthy` |
| Production-profile backend boots | **PASS** | Same image, `SPRING_PROFILES_ACTIVE=prod`, generated 64-char JWT secret, generated AES-256 key, exact CORS origin → `Started YemomBizGrowApplication in 17.2s` |

## 2. Authentication verification (against the deployed backend)

`bash infrastructure/scripts/staging-auth-audit.sh` — 22 raw-HTTP checks,
**22/22 PASS**:

| Lifecycle step | Verified behavior |
| --- | --- |
| Registration | 201 + session; access token in body only (never in a URL) |
| Refresh cookie | `HttpOnly`, `Path=/api/v1/auth`, explicit `SameSite` |
| CSRF cookie | issued, readable by design (double-submit) |
| `/auth/me` | 200 with bearer + correct user; **401 without a token** |
| Refresh without CSRF header | **403** (double-submit enforced) |
| Refresh with CSRF header | 200; **both** the access token and the cookie rotate |
| Foreign organization data | denied (404/403) |
| CSV export | **200 `text/csv` with bearer** (the fixed path); **401 without** |
| Logout | 204; refresh family revoked (refresh → 401); access token bounded by its 15-min TTL per the documented trade-off (§6, F1) |
| Login | 200 with registered credentials; generic 401 for wrong ones (no user enumeration) |

Browser-level: **44/44 Playwright executions** (22 scenarios × Chrome +
Chromium) including the network-contract tests that prove Sign in and
Create account issue exactly one `fetch` POST to `/api/v1/auth/{login,register}`,
never a document navigation, and never credentials in a URL.

## 3. Security audit (production profile)

Verified against the prod-profile instance on port 8083:

| Check | Result |
| --- | --- |
| Startup validation with real-grade secrets | passes; dev-grade secrets are refused by the existing dedicated tests (`ProductionProfileIT`, `SecurityProfileMode`) |
| `springdoc` / Swagger UI | **404** (disabled in prod) |
| `/actuator/metrics`, `/actuator/env` unauthenticated | **401** (no information disclosure) |
| `/actuator/health/readiness` | 200 (the orchestrator probe) |
| CORS preflight, allowed origin | **200** |
| CORS preflight, disallowed origin | **403** (exact allowlist, no wildcard) |
| Cookie policy in prod | defaults `SameSite=None` + `Secure=true`; `None`+insecure is rejected by `StartupValidator`; local audit ran `Lax`+insecure explicitly because plain HTTP cannot carry Secure cookies |
| Error envelopes | generic messages, `traceId` for correlation, no stack traces |

## 4. Integration verification (honest result: NOT verified)

| Provider | Status | Evidence |
| --- | --- | --- |
| Chapa | **UNVERIFIED** | `verify-providers.mjs` → SKIP: no `CHAPA_SECRET_KEY` |
| Google OAuth / Analytics | **UNVERIFIED** | SKIP: no client credentials |
| OpenAI | **UNVERIFIED** | SKIP: no API key |
| SMTP | **UNVERIFIED** | SKIP: no relay credentials |

No credential is available in this environment, so **no live integration
was tested and none is claimed to work**. The harness
(`infrastructure/scripts/verify-providers.mjs`) and the per-provider manual
checklists in `docs/DEPLOYMENT.md` are in place and exit non-zero on a
failing provider. The Chapa integration's *logic* (signature validation,
idempotency, replay/downgrade protection, amount matching) is covered by
unit tests — that is verified code, not a verified service.

## 5. Database & migration verification

| Check | Result |
| --- | --- |
| Flyway history | **19/19 migrations applied, 0 failed**, latest: `V19 add google oauth code verifier` |
| Schema | 27 tables present |
| Connectivity (the deployed path) | backend container → postgres:5432 reachable; → redis:6379 reachable |
| Concurrent-write integrity | covered by `OrganizationConcurrencyIT` (real Postgres, Testcontainers) |
| Backup/restore | `postgres-backup.sh verify` drill passes (prior cycle, unchanged) |

## 6. Findings and remediation

| ID | Severity | Finding | Remediation |
| --- | --- | --- | --- |
| B1 | **BLOCKER** | Live Chapa / Google / OpenAI / SMTP never tested against real services | Obtain staging credentials; run `verify-providers.mjs` and the four manual checklists in `DEPLOYMENT.md`; record results in the release ticket |
| B2 | **BLOCKER** | No public HTTPS staging deployment exercised — the `SameSite=None; Secure` cookie path over real HTTPS and the real CDN/proxy topology are unproven | Deploy the compose stack (or platform equivalents) to a public staging domain with `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=None`, exact `CORS_ALLOWED_ORIGINS`; re-run `staging-auth-audit.sh` and the Playwright suite against it |
| B3 | **BLOCKER (ops)** | CI frontend image job requires the `NEXT_PUBLIC_API_BASE_URL` repository variable (by design) | Configure the variable in GitHub → Settings → Variables before the next release run |
| F1 | P2 — accepted, documented | Access token remains valid up to 15 min after logout (stateless JWT; refresh family revoked immediately) | Documented in `AUTHENTICATION_AND_SECURITY.md` §Known limitations #4; the compensating control is the 15-min TTL. Revisit (token-version claim) only if a threat model justifies per-request DB checks |
| F2 | P2 — environment only | This audit machine's `PEMHTTPD-x64` service squats host port 8080 | Machine-specific: gitignored `docker-compose.override.yml` publishes the backend on 8082; not present in CI/production |
| F3 | P3 | Aggregate `/actuator/health` is 503 while SMTP is unconfigured | Expected: mail becomes a real health component in prod. Alert on readiness (serves traffic) and aggregate health (config/SMTP broken) separately |
| F4 | P3 | Deprecation noise: Mockito self-attaching agent warning in test output | Cosmetic; address when upgrading Mockito |

No new P0 or P1 defect was found in this audit.

## 7. Performance & reliability

- Load (deployed container stack, readiness probe, 300 requests, concurrency 20): **475.9 req/s, p50 12 ms, p95 174 ms, p99 359 ms, 0.00% errors**.
- Prior-cycle drills still standing: multi-instance Redis failure drill (both instances stay up, throttling holds, automatic recovery), backup/restore verify drill.
- The login throttle and AI guard degrade to bounded in-process limiters with the `SECURITY CONTROL DEGRADED` metric (`/actuator/metrics`, token-gated) — alert rules in `DEPLOYMENT.md`.

## 8. Final release recommendation

### **READY FOR STAGING — PRODUCTION BLOCKED**

Everything verifiable without a public staging environment and without live
provider credentials has been verified and passes:

- deployment artifact integrity (correct API URL baked, guard enforced),
- the complete authentication lifecycle (22/22 raw-HTTP checks, 44/44 browser),
- the production profile's security posture (actuator locked down, springdoc
  off, CORS exact, startup validation),
- database migrations (19/19) and connectivity,
- performance baseline (476 req/s, 0 errors).

**Production is blocked exclusively by B1–B3**, all of which require assets
outside this repository: staging credentials, a public HTTPS staging
deployment, and one GitHub repository variable. Resolving them and
re-running the documented checklists is the path to reclassification; the
codebase itself is not the blocker.

---

*Commands executed in this audit: `staging-auth-audit.sh` (22 checks),
`verify-providers.mjs`, `load-test.mjs`, `docker compose config/build/up`,
`psql` inspection, prod-profile instance boot on port 8083.*
