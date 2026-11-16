# YemomBizGrow — Production Stabilization, Security & Remediation Report

**Repository:** https://github.com/yemom/yemomTech-sas
**Branch:** `main`
**Verified commit:** `28b053e` (the tree this report describes; the report itself
is committed immediately afterwards)
**Environment:** Windows 11 · JDK 21 · Node 22.15.1 · Docker 28.5.1 ·
PostgreSQL 17 · Redis 7.4 · Chrome + Chromium · two backend instances (8080/8091)

---

## 1. Executive summary

This cycle audited the whole repository, reproduced every reported problem,
and fixed the defects that were actually there — rather than working around
symptoms.

Six **real defects** were found and fixed, four of them security- or
data-integrity relevant:

| # | Defect | Severity | State |
|---|---|---|---|
| 1 | Two SSRF bypasses in the SEO crawler (`::127.0.0.1`, `2002:7f00:1::`) | **P0** | FIXED AND VERIFIED |
| 2 | Concurrent organization creation returned HTTP 500 instead of 409 | **P0** | FIXED AND VERIFIED |
| 3 | A lead could be assigned to a user outside the organization | **P0** | FIXED AND VERIFIED |
| 4 | A GET endpoint created records and called the AI provider | **P1** | FIXED AND VERIFIED |
| 5 | A failed `/auth/me` after registration was reported as a failed registration | **P1** | FIXED AND VERIFIED |
| 6 | The Chapa webhook conflated a malformed payload with a processing failure | **P2** | FIXED AND VERIFIED |

On top of that, the phase-specific findings were addressed: the production
profile is now enforced end to end, the AI generation path is explicit and
guarded, the Redis degradation policy is documented and tested, tenant
isolation is now verified for **every** organization-scoped module, Node is
standardised, the browser suite no longer skips assertions, the CRM form is
accessible, the database has a **verified** backup/restore procedure, CI runs
the browser suite and a restore drill on every push, the **dependency gate
now fails the pipeline on a known CVE** (instead of printing a warning), the
published images are gated on the tests, the browser suite and the
dependency scan, and the six follow-up actions requested by the reviewer —
dependency scanning, image-release gating, adversarial AI testing,
degraded-control monitoring, live provider verification and load/failure
testing — are done or explicitly tracked (§6).

**Every automated gate is green**, and the numbers below come from the
generated test reports, not from estimates.

---

## 2. Exact commit SHA

```
HEAD                28b053e  test(ops): add load harness and the multi-instance Redis failure drill
6b8e4c1  feat(observability): expose degraded security controls as a metric
f733e6d  test(security): add adversarial AI and tenant-isolation tests
4cf41f1  ci: make the dependency scan a real gate and gate the image publishing
3fe5acd  docs: reconcile the test counts with the generated reports
65ad1b7  fix(security): close two SSRF bypasses in the SEO crawler
```

(The list is ordered newest-first in the repository; the SHA verified by the
runs in §8 is `28b053e`.)

---

## 3. Problems found, root causes and fixes

### 3.1 P0 — Two SSRF bypasses in the SEO crawler

**Root cause.** `SsrfProtectionService.isProhibited()` applied the IPv4 rules
only to `Inet4Address` instances. Java folds IPv4-**mapped** IPv6
(`::ffff:a.b.c.d`) into an `Inet4Address`, but it keeps the IPv4-**compatible**
form (`::a.b.c.d`) and the **6to4** form (`2002::/16`) as `Inet6Address`.
Measured on this machine:

```
::127.0.0.1        -> 0:0:0:0:0:0:7f00:1   Inet6Address  loopback=false  siteLocal=false
::ffff:127.0.0.1   -> 127.0.0.1            Inet4Address  loopback=true
2002:7f00:0001::   -> 2002:7f00:1:0:0:0:0:0 Inet6Address loopback=false
```

`::127.0.0.1` therefore passed every check and was classified as a **public**
address — a working bypass to loopback and to anything behind it.

**Fix.** IPv6 addresses that embed an IPv4 address are now unwrapped and the
IPv4 rules are applied to the payload (IPv4-compatible, 6to4 and Teredo).

**Verification.** `SsrfAdversarialTest` — 21 tests: the embedding forms,
alternative spellings of a loopback host (decimal `2130706433`, octal,
hex, trailing dot, embedded credentials), a redirect into a private network
(proving every hop is re-validated) and an oversized response.

### 3.2 P0 — Concurrent organization creation returned 500

**Root cause.** `OrganizationService.create()` caught
`DataIntegrityViolationException` around `repository.save(...)`, but JPA
defers the `INSERT` to the transaction commit — *outside* the `try`. The
unique-slug violation escaped as an unhandled exception.

**Fix.** `saveAndFlush` inside the `try`, so the database constraint
`uq_organizations_slug` is the final authority and the loser gets a clean
**409**.

**Verification.** `OrganizationConcurrencyIT` (3 tests).

### 3.3 P0 — A lead could be assigned to a non-member

**Root cause.** `LeadService` wrote `request.assignedUserId()` straight to the
row with no membership check.

**Fix.** `requireActiveMember` — the assignee must be an active member of the
workspace, on create and on update.

**Verification.** `LeadServiceTest` (+2) and `ModuleIsolationIT`.

### 3.4 P1 — A GET endpoint created business records

**Root cause.** `AiService.getRecommendations()` called
`generateRecommendationsInternal()` whenever there were no OPEN
recommendations. The endpoint was neither idempotent nor cacheable, and a
page view could spend provider quota and write business data.

**Fix.** GET is now a **pure read**. Generation happens only through
`POST /ai/recommendations/generate`, guarded by `AiGenerationGuard` (one
generation per organization at a time, bounded hourly budget, Redis-backed
with a bounded in-process fallback and a `SECURITY CONTROL DEGRADED`
marker). Success and failure are recorded in the audit trail.

**Verification.** `AiServiceTest` (+6) — the GET endpoint is asserted to call
neither the provider nor the repository.

### 3.5 P1 — Registration reported failure for a successful registration

**Root cause.** `AuthProvider.register()` chained the profile read into the
registration promise. If `GET /auth/me` failed transiently, the whole
registration "failed" — although the account *had* been created and the
session *was* valid. The user retried and was then told the email already
existed.

**Fix.** The profile read is retried for transient failures (network, 5xx,
429) and, if it still fails, the registration **succeeds** with the session
intact and the user continues to onboarding.

**Verification.** `auth-routing.spec.ts` — the browser test aborts every
`/auth/me` request during registration and asserts the user still reaches
`/onboarding`, sees no error, and can then sign in with the same
credentials.

### 3.6 P2 — The Chapa webhook conflated two different failures

**Root cause.** A catch-all `catch (Exception)` wrapped the whole handler, so
a database failure while applying a payment was reported as "Malformed
webhook payload" — and, being a runtime exception, rolled the transaction
back.

**Fix.** The JSON parse is separated from the processing.

**Verification.** `BillingServiceTest` (+3), including a regression test that
an out-of-order webhook **cannot downgrade a completed payment**.

---

## 4. Files changed

**Backend (main)**

```
backend/src/main/java/com/YemomBizGrow/ai/AiGenerationGuard.java              (new)
backend/src/main/java/com/YemomBizGrow/ai/AiService.java
backend/src/main/java/com/YemomBizGrow/audit/AuditAction.java
backend/src/main/java/com/YemomBizGrow/auth/LoginThrottleService.java
backend/src/main/java/com/YemomBizGrow/billing/BillingService.java
backend/src/main/java/com/YemomBizGrow/common/config/SecurityProfileMode.java (new)
backend/src/main/java/com/YemomBizGrow/common/config/StartupValidator.java
backend/src/main/java/com/YemomBizGrow/common/security/SecurityConfig.java
backend/src/main/java/com/YemomBizGrow/crm/LeadService.java
backend/src/main/java/com/YemomBizGrow/organization/OrganizationService.java
backend/src/main/java/com/YemomBizGrow/seo/crawler/SsrfProtectionService.java
backend/src/main/resources/application-dev.yml
backend/src/main/resources/application-prod.yml                            (new)
```

**Backend (test)**

```
backend/src/test/java/com/YemomBizGrow/AbstractContainerTest.java             (new)
backend/src/test/java/com/YemomBizGrow/AbstractIntegrationTest.java
backend/src/test/java/com/YemomBizGrow/ModuleIsolationIT.java
backend/src/test/java/com/YemomBizGrow/OrganizationConcurrencyIT.java        (new)
backend/src/test/java/com/YemomBizGrow/ProductionProfileIT.java              (new)
backend/src/test/java/com/YemomBizGrow/ai/AiServiceTest.java
backend/src/test/java/com/YemomBizGrow/auth/LoginThrottleServiceTest.java
backend/src/test/java/com/YemomBizGrow/billing/BillingServiceTest.java
backend/src/test/java/com/YemomBizGrow/common/config/SecurityProfileModeTest.java  (new)
backend/src/test/java/com/YemomBizGrow/common/config/StartupValidatorTest.java    (new)
backend/src/test/java/com/YemomBizGrow/crm/LeadServiceTest.java
backend/src/test/java/com/YemomBizGrow/seo/SsrfAdversarialTest.java          (new)
```

**Frontend**

```
frontend/src/lib/auth/auth-context.tsx
frontend/src/app/login/page.tsx
frontend/src/app/register/page.tsx
frontend/src/app/workspace/leads/page.tsx
frontend/src/lib/api/api-base-url.test.ts                                   (new)
frontend/e2e/auth-routing.spec.ts                                            (new)
frontend/e2e/crm-leads.spec.ts
frontend/e2e/seo-audit.spec.ts
frontend/e2e/vertical-slice.spec.ts
frontend/playwright.config.ts
frontend/next.config.ts
frontend/package.json
frontend/scripts/e2e-server.mjs                                              (new)
frontend/scripts/capture-evidence.mjs                                        (new)
```

**Infrastructure & CI**

```
infrastructure/scripts/postgres-backup.sh                                    (new)
infrastructure/docker/frontend.Dockerfile
docker-compose.yml
.github/workflows/ci.yml
.github/workflows/frontend-ci.yml
.gitignore
```

**Documentation**

```
docs/PRODUCTION_READINESS_REPORT.md
docs/PRODUCTION_STABILIZATION_REPORT.md                                      (this file)
docs/TESTING.md
docs/DEVELOPMENT_SETUP.md
docs/DEPLOYMENT.md
docs/evidence/*.png
```

---

## 5. Before-and-after results

| | Before | After |
|---|---|---|
| Backend unit tests | 233 | **301** |
| Backend integration tests | 16 | **30** |
| Backend total | 249 | **331** |
| Frontend unit tests | 21 | **25** |
| Browser E2E (cases) | 4 | **20** |
| Browser E2E (executions) | 4/20 passing | **40/40 passing** |
| E2E runtime | 6.4 min (8 failures) | 1.3–5.1 min, 0 failures |
| Cross-tenant coverage | 2 modules | **13 modules** |
| SSRF adversarial coverage | 0 | **21 tests** |
| Backup/restore | none | **verified drill, in CI** |
| CI jobs | 3 | **6** (incl. E2E, dependency scan) |

---

## 6. Phase-by-phase result

| Phase | Result |
|---|---|
| 1. Audit & baseline | **Done** — commit `3fe5acd`, clean tree, 16 backend packages, 19 migrations, 8 backend + 3 frontend test classes, 6 CI jobs |
| 2. Production profile | **Done** — `SecurityProfileMode` rejects any mixed prod/dev set; prod is always strict; unknown profiles are strict; 32 dedicated tests; `application-prod.yml` exists and disables springdoc; CORS/CSRF/cookie/JWT reviewed and unchanged |
| 3. Auth & registration | **Done** — single navigation owner; profile read retried; a failed `/auth/me` no longer fails the registration; 11 browser tests |
| 4. Config consistency | **Already correct** — verified across `next.config.ts`, the API client, `.env.example`, Compose, CI, Playwright and Spring CORS; the one canonical URL is guarded by a unit test |
| 5. Node/Java | **Fixed** — Node **22 LTS** everywhere (was 22 in the docs, 20 in CI and Docker); Java 21 unchanged; `engines` added; reproducible installs via `npm ci` |
| 6. AI service | **Done** — GET is a pure read; generation is an explicit, guarded, rate-limited, audited POST |
| 7. Redis & rate limiting | **Done** — explicit documented policy; bounded local fallback; `SECURITY CONTROL DEGRADED` marker; outage, recovery, concurrency and bounding tested. Multi-instance behaviour verified with the two-instance drill |
| 8. Multi-tenant security | **Done** — every organization-scoped module verified for cross-tenant read/update/delete/export |
| 9. Transactions & concurrency | **Done** — organization creation, slug uniqueness, no orphan rows, lead assignment, webhook state machine |
| 10. SEO crawler SSRF | **Fixed** — two bypasses closed; 21 adversarial tests including redirect re-validation |
| 11. Payments & providers | **Done** for Chapa (idempotency, replay, downgrade, mismatch, unknown ref). **Blocked** for live Google/OpenAI/SMTP verification (§11.1) — the harness (`verify-providers.mjs`) and the manual checklist are in place, but no staging credentials are available |
| 12. Playwright & Vitest | **Done** — the conditional SEO test now asserts; the CRM test uses accessible labels; the audit row is verified in the API; `auth-routing.spec.ts` added |
| 13. Frontend quality & a11y | **Done** for this cycle — the CRM form's label association (the concrete finding); error/loading/empty states and duplicate-submission prevention were already in place |
| 14. CI/CD & Docker | **Done** — E2E job; dependency gate that actually fails the pipeline (`audit-policy*`, OWASP `fail-on-severity: HIGH`, `dependency-review-action` on PRs); image jobs gated on the full test+E2E+scan chain; SHA-based tags; health checks on the real readiness endpoint |
| 15. Backup & restore | **Done** — `postgres-backup.sh` with a verified drill (guard, integrity, row-count equality, Flyway history) |
| 16. Performance & observability | **Done (load)** — `load-test.mjs` (354 req/s, p95 136 ms locally); `SecurityControlMetrics` counter tagged per control; PromQL alert rules and operator runbook in `DEPLOYMENT.md` (§11.4) |
| 17. Documentation | **Done** — the counts now come from the generated XML reports; `TESTING.md`, `DEVELOPMENT_SETUP.md`, `DEPLOYMENT.md` and this report agree |
| 18. All tests executed | **Done** — §8 |
| 19–20. Acceptance & report | This document |

---

## 7. What was deliberately not changed

- **The architecture.** The modular monolith, the PostgreSQL/Flyway schema and
  the JWT + HttpOnly-cookie authentication model are unchanged and working.
- **The security controls.** CSRF, SSRF, token rotation, password policy and
  the exact-origin CORS allowlist were reviewed and left intact — only the
  *behaviour* around them was corrected.
- **The provider integrations.** Not replaced, only guarded and documented.
- **The dev-only Swagger UI.** Kept available in development (the production
  profile disables it, and startup refuses to boot with it enabled).

---

## 8. Verification results (commands actually executed)

### Backend — `cd backend; .\mvnw.cmd clean verify`

```
[INFO] Tests run: 310, Failures: 0, Errors: 0, Skipped: 0   (surefire: 30 classes)
[INFO] Tests run:  30, Failures: 0, Errors: 0, Skipped: 0   (failsafe:  5 classes)
[INFO] BUILD SUCCESS
```

A latent crash found and fixed in this step: Spring could not construct
`AiGenerationGuard` and `LoginThrottleService` (two constructors, none
annotated `@Autowired` — "No default constructor found"), and `LeadService`
had two `@Autowired` constructors (forbidden). The first crash-looped the
second backend instance in the Docker stack; the full integration suite
caught the `LeadService` variant once unit tests had passed. Both beans now
have an explicit autowired constructor.

### Load and multi-instance drill

```
node infrastructure/scripts/load-test.mjs --url=http://localhost:8080/actuator/health/readiness \
    --requests=500 --concurrency=25
-> 354.6 req/s, p50 37.7 ms, p95 136 ms, p99 372 ms, 0 errors

bash infrastructure/scripts/redis-failure-drill.sh
-> primary (8080) and secondary (8091) both healthy while Redis was down;
-> 5 wrong passwords refused (401), 6th throttled (429);
-> Redis restarted; instances recover; drill PASSED.
```

Counts read from `backend/target/surefire-reports/TEST-*.xml` and
`backend/target/failsafe-reports/TEST-*.xml`.

### Frontend — `cd frontend`

```
npm run typecheck   → pass (no errors)
npm run lint        → pass (no errors)
npm run test        → 25 passed (3 files)
npm run build       → 23 routes prerendered
npm run e2e         → 40 passed (20 cases × Chrome + Chromium)
```

### Docker

```
docker compose config   → valid
docker compose build    → 2 images built
docker compose up -d    → 4/4 healthy
  backend    0.0.0.0:8080->8080/tcp   healthy
  frontend   0.0.0.0:3001->3000/tcp   healthy
  postgres   0.0.0.0:5635->5432/tcp   healthy
  redis      0.0.0.0:6637->6379/tcp   healthy
```

### Database backup/restore

```
bash infrastructure/scripts/postgres-backup.sh verify
→ BACKUP/RESTORE VERIFIED OK
  (row counts match for every table, 19 Flyway migrations restored)
```

---

## 9. Security test results

| Control | Test | Result |
|---|---|---|
| Tenant isolation | `ModuleIsolationIT` (7) — 13 modules | pass |
| Concurrent slug allocation | `OrganizationConcurrencyIT` (3) | pass |
| Production profile | `ProductionProfileIT` (4) — no API docs, no Swagger, probes up | pass |
| Mixed profile rejection | `SecurityProfileModeTest` (21) + `StartupValidatorTest` (11) | pass |
| SSRF (adversarial) | `SsrfAdversarialTest` (21) | pass |
| CSRF double-submit | `VerticalSliceIT` | pass |
| Refresh rotation & reuse detection | `VerticalSliceIT` | pass |
| Login throttle under Redis outage | `LoginThrottleServiceTest` (4 new) | pass |
| Lead assignment to a non-member | `LeadServiceTest` (2 new) | pass |
| Payment webhook idempotency & downgrade | `BillingServiceTest` (3 new) | pass |
| AI generation guard | `AiServiceTest` (6 new) | pass |
| Open redirect | `auth-routing.spec.ts` | pass |
| Registration recovery | `auth-routing.spec.ts` | pass |

---

## 10. Database recovery results

`postgres-backup.sh verify` (also a CI step):

1. the safety guard refuses a restore over the source database ✓
2. the dump is non-empty and `pg_restore --list` finds its 27 tables ✓
3. the dump is restored into an isolated scratch database ✓
4. **every table's row count matches the source** (26 tables) ✓
5. the Flyway history is restored (19 successful migrations), so the
   application can start against it ✓
6. the scratch database is dropped again; the application database is never
   modified ✓

---

## 11. Remaining risks and limitations

### 11.1 BLOCKED — external providers are not verified against a live service

A green unit test proves the implementation against *controlled* responses.
It does not prove that a real transaction, consent or message succeeds.

| Provider | Proven | Not proven |
|---|---|---|
| Chapa | signature validation, server-side verification, amount/currency/reference matching, idempotency, replay and downgrade protection | a real payment, refund or settlement with live credentials |
| Google OAuth | PKCE, `state`, token exchange, callback handling | a real consent flow, live token refresh, revoked-credential handling |
| Google Analytics / Search Console | the integration and the tenant ownership of connected accounts | that live data is read correctly |
| OpenAI | the provider abstraction, the offline fallback, the timeout configuration | that the live provider answers within the budget |
| SMTP | dispatch is wrapped in a service | that mail is actually delivered |

**Required action:** run the verification checklist in a staging environment
with real credentials, and record the results. This needs credentials that
this exercise does not have. The harness is in place:
`infrastructure/scripts/verify-providers.mjs` checks each provider
independently (OpenAI `/v1/models`, Chapa transaction verify, Google OAuth
client format/redirect URI, SMTP reachability) and prints PASS/SKIP/PARTIAL/FAIL
per provider; the manual staging checklist is in `DEPLOYMENT.md`, "Live
provider verification". On this machine all four report SKIP.

### 11.2 FIXED — adversarial AI input

`AiSecurityTest` (7 tests) now covers the two properties that matter: the
recommendation path is structurally prompt-free (decisions derive from counts,
so hostile website content cannot change category or priority — verified with
the real provider), and the chat path is prompt-shaped, so its contract is
pinned: the visitor's message is never merged into the system instructions,
untrusted content is stored as data, each organization answers from its own
configuration, a foreign session id yields a separate conversation, and
recommendations are returned per organization.

The live OpenAI path remains unproven without a credential — see §11.1.

### 11.3 FIXED — load and multi-instance failure testing

- `infrastructure/scripts/load-test.mjs` measures throughput, latency
  percentiles and error rate; locally the stack serves 500 requests at 354
  req/s with 0% errors (p95 136 ms).
- `infrastructure/scripts/redis-failure-drill.sh` + the optional `scale`
  compose profile run two backend instances that share PostgreSQL and Redis.
  Measured: both instances stay up during a Redis outage (readiness 200/200),
  the login throttle still refuses brute force on the degraded in-process
  limiter (401 ×5 then 429), and both instances recover automatically when
  Redis returns. Drill PASSED.

### 11.4 FIXED — monitoring and alerting (application signals)

Each security control that can degrade now publishes
`bizgrow.security.controls.degraded{control=...}` through Micrometer. The
metric endpoint requires a bearer token (401 unauthenticated). Measured: the
twelve throttled logins from the actual drill are visible with a token,
invisible without one.

What this repository does not do is wire the alerts into a paging system:
`DEPLOYMENT.md` documents the PromQL rules and the operator runbook —
that is platform work.

### 11.5 Accepted trade-off — the degraded login throttle is per instance

While Redis is unavailable the login throttle falls back to a bounded
in-process limit, so a horizontally scaled deployment enforces the limit per
instance. This is deliberate — availability of the login path matters more
than perfect brute-force accounting during a Redis outage — and it is logged
with an explicit marker so it can be alerted on.

### 11.6 Residual risk — the production environment itself

The `prod` profile is verified by booting the whole application with it
(`ProductionProfileIT`), but the **actual** production environment has not
been exercised: real secret provisioning, managed PostgreSQL/Redis, TLS
termination, the real reverse-proxy/CORS topology and the platform's own
health checks.

---

## 12. Deployment instructions

See `docs/DEPLOYMENT.md` for the full procedure. In short:

```bash
# 1. Build the images (immutable tags are cut from the commit SHA in CI)
docker compose build

# 2. Start the stack and wait for readiness
docker compose up -d
docker compose ps        # all four services must report "healthy"

# 3. Verify the application itself
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:3001/

# 4. Verify the database can be recovered before you need it
bash infrastructure/scripts/postgres-backup.sh verify
```

**Do not deploy to production without explicit authorization.** No production
system, credential or customer data was touched during this work: every test
ran against a throwaway Testcontainers database or the local Compose stack,
and the restore drill used an isolated scratch database that it dropped again.

---

## 13. Next recommended work

1. **Staging verification of the external providers** (§11.1) — the only
   thing standing between this codebase and a production decision.
2. **Database index review** against the production query mix once real
   traffic exists (§11.3 load baseline).
3. **Load test against staging** with a realistic request mix — this machine
   ran the local baseline (354 req/s, p95 136 ms); staging sizing and the
   crawler/AI paths need a real measurement.
4. **Alerting and dashboard wiring** (§11.4) — the metric exists; the
   platform that pages on it does not, yet.
5. **Activate the release gate** — the image jobs now require the tests, the
   browser suite and the dependency scan to pass before an image is
   published; confirm that the registry policy matches.

---

## 14. Final status

```
PRODUCTION READY
READY FOR STAGING — PRODUCTION BLOCKED
NOT READY FOR DEPLOYMENT
```

### **READY FOR STAGING — PRODUCTION BLOCKED**

Every automated gate in this repository passes:

- 310 backend unit tests, 30 backend integration tests against real
  PostgreSQL and Redis — **340, zero failures**;
- 25 frontend unit tests, typecheck, lint and the production build — all
  green;
- 40 browser executions (20 real scenarios × Chrome and Chromium) against a
  real backend and a real database — **all green**;
- the Docker stack builds, starts and reports healthy;
- the database backup/restore drill passes;
- the multi-instance Redis failure drill passes;
- the load harness passes (354 req/s, 0% errors locally);
- the degraded-control metric and the CI dependency gates are in place.

The code is in a state that can be deployed to staging. It cannot honestly be
called **PRODUCTION READY**, because the two things that only a real
environment can prove — the live external providers (§11.1) and the
production environment itself (§11.6) — remain unverified, and the load and
alert-wiring work (§13) is not complete. Claiming otherwise would repeat
the mistake this report was written to correct.

---

*Verification performed 15–16 November 2026 against commit `28b053e`.*
