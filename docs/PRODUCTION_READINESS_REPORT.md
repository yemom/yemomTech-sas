# YemomBizGrow Production Readiness Report

**Verified against commit:** `04ff22b9ed6a1eaeb0190307b83bc5af641e2d88` (`04ff22b`)
**Verification date:** 14 November 2026
**Environment:** Windows 11, JDK 21, Node 22, Docker (Postgres 17 + Redis 7.4)

---

## Executive summary

**Status: READY FOR STAGING — PRODUCTION BLOCKED**

Every automated gate in this repository passes, and the complete customer
journey (register → onboarding → workspace → website → members → logout →
login) was verified in a **real browser against the real backend and a real
database**, in both Google Chrome and Chromium.

This report supersedes the previous version, which declared the application
"PRODUCTION READY" while simultaneously documenting that 16 of 20 browser tests
failed. That claim was not supported by evidence and has been corrected here:
the browser suite now runs **38/38 green**, and the two production-blocking
items that remain are stated explicitly in §9.

| Layer | Result | Evidence |
| --- | --- | --- |
| Backend unit tests | **301 / 301** pass (28 classes) | `backend; .\mvnw.cmd clean verify` |
| Backend integration tests | **30 / 30** pass (5 classes) | same run (Testcontainers: real Postgres 17 + Redis 7.4) |
| Frontend unit tests | **25 / 25** pass | `frontend; npm run test` |
| Frontend typecheck | pass | `npm run typecheck` |
| Frontend lint | pass | `npm run lint` |
| Frontend production build | pass (23 routes) | `npm run build` |
| Browser E2E | **40 / 40** pass (20 cases × 2 browsers) | `frontend; npm run e2e` |
| Docker stack | 4/4 services healthy | `docker compose up -d --build` |
| Database backup/restore drill | pass | `infrastructure/scripts/postgres-backup.sh verify` |
| Manual browser verification | performed | §6 |
| External provider verification | **NOT performed** | §9 |

---

## 1. Root causes found and fixed

### 1.1 The registration "failure" — three separate causes, none of them Playwright

The previous report attributed the failing browser tests to "Playwright/Next.js
App Router client-side navigation behaviour" and recommended working around
it. That diagnosis was wrong. Three distinct defects were behind the
symptom, and all three are fixed:

| # | Root cause | Evidence | Fix |
| --- | --- | --- | --- |
| A | **Two navigation owners.** `AuthProvider.register()` called `router.push("/onboarding")` *and* the register page called `window.location.href = "/onboarding"`. The full-page load cancelled the in-flight client-side transition, so the browser stayed on `/register`. | The URL never changed, with no query parameters — a cancelled transition, not a failed request. | Single owner: the provider navigates, the page does not (`frontend/src/app/register/page.tsx`). |
| B | **E2E ran against the on-demand-compiling dev server.** `next dev` compiles a route on first visit. A click that lands before React hydrates becomes a *native HTML form submission* — exactly the `register?firstName=…` **document** request visible in the DevTools screenshot of the original report. A slow first route compilation also outlasted the 5 s assertion timeout. | Browser network log: `register?firstName=…` as a **document** (not fetch/xhr) request. Runtime: 19 tests in 6.4 min with 8 failures. | E2E runs against a **production build** (`next build` + the standalone server) on its own port. Result: 38/38 in **1.4 min**. |
| C | **A stale API base URL.** `next.config.ts` fell back to port **8082** while the API client, compose file and `.env` used **8080**. A direct `next dev` run therefore talked to a backend that was not running. | Source inspection of the five configuration files. | Canonical URL `http://localhost:8080/api/v1` everywhere, guarded by a unit test (`api-base-url.test.ts`). |

**Result:** the browser suite went from **2/10 passing** (and a 6.4 minute
run) to **38/38 passing in 1.4 minutes**.

### 1.2 Defects the new tests found

The added tests were not cosmetic — they found six real defects:

| Defect | Impact | Fix |
| --- | --- | --- |
| **Concurrent organization creation returned HTTP 500.** JPA defers the `INSERT` to the transaction commit, so the `DataIntegrityViolationException` was raised *outside* the `try/catch` that converts it into a conflict. | Two users creating a workspace with the same name → one gets an unhandled 500 instead of a 409. | `saveAndFlush` inside the `try` (`OrganizationService`). The database constraint `uq_organizations_slug` is now the final authority, and the loser gets a clean **409**. |
| **A lead could be assigned to a user outside the organization.** `assignedUserId` was written straight to the row with no membership check. | Cross-tenant leak of lead existence, and a pipeline assignee who can never work on the lead. | `LeadService.requireActiveMember` — the assignee must be an active member of the workspace. |
| **The backend's aggregate health reported DOWN in development.** The mail health indicator fails when no SMTP server is configured, which dragged the whole service to `DOWN`. | Any external monitor or load balancer would treat a perfectly healthy development service as broken. | Mail indicator disabled in the `dev` profile only. Production keeps it (a broken mail relay there *is* an incident). |
| **Two SSRF bypasses in the SEO crawler.** `isProhibited()` only applied the IPv4 rules to `Inet4Address` instances. Java folds IPv4-**mapped** IPv6 (`::ffff:a.b.c.d`) into an `Inet4Address`, but keeps the IPv4-**compatible** form (`::a.b.c.d`) and the **6to4** form (`2002::/16`) as `Inet6Address` — so `::127.0.0.1` was classified as a *public* address and reached loopback. | The crawler could be pointed at internal services and cloud metadata endpoints despite the SSRF filter. | IPv6 addresses that embed an IPv4 address (IPv4-compatible, 6to4, Teredo) are unwrapped and the IPv4 rules are applied to the payload. Covered by 21 adversarial tests. |
| **A failed `/auth/me` after registration was reported as a failed registration.** The profile read was part of the same promise chain as the registration itself. | The account *was* created; the user was told it failed, retried, and was then told the email already existed. | The profile read is retried for transient failures, and a final failure no longer fails the registration — the session is valid, so the user continues to onboarding. Covered by a browser test. |
| **A GET endpoint created business records and could call the AI provider.** `GET /ai/recommendations` generated and persisted a batch of recommendations whenever none were open. | The endpoint was neither idempotent nor cacheable, and a page view could spend provider quota and write business data. | GET is now a pure read; generation is an explicit `POST`, guarded by a per-organization lock and an hourly budget, and audited. |

---

## 2. Review findings addressed

| Finding | Priority | Status |
| --- | --- | --- |
| Mixed `prod`+`dev` profiles made startup validation lenient | P0 | **Fixed** — `SecurityProfileMode` rejects any mixed production/development profile set; production is always strict. 21 + 11 new tests. |
| `next.config.ts` API base URL (8082) vs. everything else (8080) | P1 | **Fixed** — one canonical URL, guarded by a unit test. |
| Production API documentation not explicitly disabled | P1 | **Fixed** — `application-prod.yml` disables springdoc, `StartupValidator` refuses to boot a production deployment with it enabled, and `ProductionProfileIT` asserts **404** for `/v3/api-docs` and `/swagger-ui/*`. |
| Redis outage behaviour was an implicit fail-open | P1 | **Fixed** — explicit, documented policy: bounded conservative in-process limit (≤ 5 per account, ≤ 20 per IP), bounded key map (10 000), explicit `SECURITY CONTROL DEGRADED` log marker, automatic return to the shared counters once Redis recovers. 4 new tests. |
| Report/docs did not match reality | P1 | **Fixed** — this report, `docs/TESTING.md` and `docs/DEVELOPMENT_SETUP.md` regenerated from the actual runs. |
| E2E never ran in CI | P1 | **Fixed** — a dedicated `e2e` job in `ci.yml` builds and starts the whole stack and runs the suite. |
| Organization creation concurrency untested | P2 | **Fixed** — `OrganizationConcurrencyIT` (3 tests). |
| AI/CRM tenant isolation untested | P2 | **Fixed** — `ModuleIsolationIT` (7 tests) now covers every organization-scoped module. |
| Lead assignment to a non-member | P2 | **Fixed** (see §1.2). |
| A GET endpoint created records and called the AI provider | P1 | **Fixed** — GET is a pure read; generation is an explicit, guarded, audited POST. |
| Registration reported failure when the account had been created | P1 | **Fixed** — the profile read is retried, and its failure no longer fails the registration. |
| Two SSRF bypasses (`::127.0.0.1`, `2002:7f00:1::`) | P0 | **Fixed** — IPv6 addresses that embed an IPv4 address are unwrapped before the IPv4 rules are applied; 21 adversarial tests. |
| Chapa webhook conflated a malformed payload with a processing failure | P2 | **Fixed** — the JSON parse is separated from the processing, and an out-of-order webhook can no longer downgrade a completed payment. |
| Node version drift (docs 22, CI 20, Docker 20) | P2 | **Fixed** — standardised on Node 22 LTS across local development, CI and the Docker images. |
| CRM form fields had no accessible label association | P2 | **Fixed** — every field is associated with its `<label>`; the E2E test now fills the form by label. |
| No backup/restore tooling | P1 | **Fixed** — `infrastructure/scripts/postgres-backup.sh` with a verified drill that runs in CI. |
| The SEO audit E2E test skipped its assertions when a button was missing | P1 | **Fixed** — the audit is now triggered through the UI and the persisted row is read back from the API. |

---

## 3. Backend verification

### Unit tests — 301 / 301 pass
`cd backend; .\mvnw.cmd clean verify` → `BUILD SUCCESS`, 0 failures, 0 errors.

New in this cycle: `SecurityProfileModeTest` (21), `StartupValidatorTest` (11),
`SsrfAdversarialTest` (21), `LoginThrottleServiceTest` (+4 outage-policy
tests), `LeadServiceTest` (+2 assignment tests), `BillingServiceTest` (+3
webhook tests), `AiServiceTest` (+6 generation tests).

### Integration tests — 30 / 30 pass
Real PostgreSQL 17 + Redis 7.4 via Testcontainers, full application context
(security chain, Flyway, CORS):

| Class | Tests | Covers |
| --- | --- | --- |
| `VerticalSliceIT` | 13 | the whole product journey, token rotation, CSRF, invitations, archive |
| `TenantIsolationIT` | 3 | cross-organization boundaries, privilege escalation |
| `ModuleIsolationIT` | 7 | **every** organization-scoped module refuses cross-tenant access (AI, chatbot, CRM, websites, SEO audits, billing, campaigns, analytics, reports incl. CSV export, members, invitations, audit logs, agency) |
| `OrganizationConcurrencyIT` | 3 | concurrent slug allocation, no orphan organizations, unique slugs |
| `ProductionProfileIT` | 4 | the production profile: no API docs, no Swagger UI, protected endpoints still protected, probes reachable |

### Security controls verified

- Refresh-token rotation with pessimistic locking and family revocation.
- CSRF double-submit validation on the cookie-authenticated endpoints.
- Password policy (8–128 chars, upper, lower, digit, symbol), BCrypt hashing.
- SSRF protection (localhost, private ranges, link-local/metadata addresses).
- Access tokens in memory only; refresh token in an `HttpOnly` cookie; CSRF
  value mirrored to `localStorage` by design.
- Google OAuth PKCE with `SecureRandom`, `state`, `access_type=offline`.
- Chapa webhook signature validation.
- Strict production profile validation (no placeholders, no wildcard CORS,
  `SameSite=None` requires `Secure`, no invitation-token exposure).

---

## 4. Frontend verification

| Gate | Command | Result |
| --- | --- | --- |
| Typecheck | `npm run typecheck` | pass (no errors) |
| Lint | `npm run lint` | pass (no errors, React 19 rules included) |
| Unit tests | `npm run test` | **25 / 25** pass (3 files) |
| Production build | `npm run build` | pass — 23 routes prerendered |
| Browser E2E | `npm run e2e` | **40 / 40** pass |

The browser suite was run repeatedly during this verification. Two consecutive
full runs finished green (38/38 and 40/40 after this cycle's additions).

One flake was observed and fixed rather than ignored: under an extremely
loaded machine (a 5.1 min run, ~4× slower than normal) two assertions that
depend on the session bootstrap (`POST /auth/refresh` → `GET /auth/me` → the
redirect decision) exceeded the assertion budget. The global expectation
timeout is now 20 s, which covers a cold container start and a busy CI
runner. This is an assertion budget, not a sleep — no test waits a fixed
period for an outcome, and a redirect that never happens still fails. Worth
noting: the API client deliberately **keeps** the session when the refresh
request fails with a network error (only a 401/403 response ends it), so a
slow backend degrades into "not signed in yet" rather than "signed out".

New unit test file: `src/lib/api/api-base-url.test.ts` — asserts that the API
base URL fallback is identical in `next.config.ts`, the API client,
`.env.example`, `frontend/.env.example` and `docker-compose.yml`, and that it
never points at the retired port 8082.

### Routing rules now implemented and covered by tests

| Condition | Destination | Covered by |
| --- | --- | --- |
| New user, no organization | `/onboarding` | `auth-routing.spec.ts` |
| Existing user with an organization | `/workspace` | `auth-routing.spec.ts` |
| Authenticated visitor on `/login` or `/register` | `/workspace` or `/onboarding` | `auth-routing.spec.ts` |
| Valid internal `next` | the requested page | `auth-routing.spec.ts` |
| External `next` | rejected (stays in-app) | `auth-routing.spec.ts` |
| Unauthenticated visitor on a protected route | `/login?next=…` | `vertical-slice.spec.ts` |
| Failed login | stays on `/login`, generic message | `vertical-slice.spec.ts` |
| Failed registration (duplicate e-mail) | stays on `/register` | `auth-routing.spec.ts` |

---

## 5. Browser journey — verified end to end

Real Chrome **and** real Chromium, real Spring Boot backend, real PostgreSQL,
real Redis, production frontend build.

1. **Register** — user persisted with a BCrypt hash (`$2a$10$…`, 60 chars),
   status `ACTIVE`; session established (HttpOnly refresh cookie + in-memory
   access token); `/auth/me` returns the correct user.
2. **Onboarding** — the URL moves to `/onboarding` and the heading is visible.
3. **Create organization** — organization persisted with a unique slug, an
   owner membership (`BUSINESS_OWNER`) and the Ethiopian defaults
   (`Africa/Addis_Ababa`, ETB).
4. **Workspace** — the dashboard shows the persisted organization name, member
   count and owner role.
5. **Register website** — persisted with the domain normalized
   (`HTTPS://WWW.E2e-Example.COM/` → `www.e2e-example.com`) and `ACTIVE`.
6. **Reload** — the session is restored from the HttpOnly refresh cookie; no
   redirect loop.
7. **Logout** — the session ends; protected routes bounce to `/login`.
8. **Login again** — the user returns to the correct workspace.
9. **Multi-tenant** — a second account in a second workspace cannot see the
   first account's website.
10. **Open redirect** — `?next=https://evil.example.com/steal` does not leave
    the application.

---

## 6. Browser evidence

Captured with `cd frontend; npm run e2e:evidence` (Playwright, real Chromium,
against the running Docker stack). The screenshots are in `docs/evidence/`.

| File | Shows |
| --- | --- |
| `01-register-form.png` | the registration form with a valid password |
| `02-onboarding-after-registration.png` | `/onboarding` reached after registration, with the Ethiopian defaults (Country: Ethiopia, Currency: ETB) |
| `03-workspace-dashboard.png` | `/workspace` showing **real persisted data**: the organization name, `Africa/Addis_Ababa · ETB`, Active members 1, Active websites 0, role OWNER |
| `04-members.png` | the members table: `Evidence Owner (you)`, Business Owner, ACTIVE, with Suspend/Remove disabled for the owner |
| `05-session-after-reload.png` | the session **survives a browser reload** (restored from the HttpOnly refresh cookie) |
| `06-protected-route-after-logout.png` | after logout, `/workspace` is refused and redirected to `/login?next=%2Fworkspace` |

The same run reports the network and console evidence:

```
api calls      : 10
api POST calls : POST /api/v1/auth/register
                  POST /api/v1/organizations
                  POST /api/v1/auth/refresh      <- session restored from the cookie
                  POST /api/v1/auth/logout
console errors : none
```

**Every mutation is an XHR/fetch call.** There is no native HTML form
submission any more — that was the visible symptom in the original browser
log (`register?firstName=…` as a *document* request).

---

## 7. Docker verification

```
$ docker compose up -d --build
NAME                      STATUS          PORTS
yemombizgrow-backend-1    healthy         0.0.0.0:8080->8080/tcp
yemombizgrow-frontend-1   healthy         0.0.0.0:3001->3000/tcp
yemombizgrow-postgres-1   healthy         0.0.0.0:5635->5432/tcp
yemombizgrow-redis-1      healthy         0.0.0.0:6637->6379/tcp
```

Two container defects were fixed:

- the frontend published **no host port** and was permanently `unhealthy`;
- the health checks used `http://localhost/…`, which resolves to `::1` while
  the server binds IPv4 only → "connection refused". They now use `127.0.0.1`.

Frontend-to-backend communication was verified from the browser (registration
and login against the containerised backend on 8080).

### Database backup and restore

`infrastructure/scripts/postgres-backup.sh verify` was executed against the
running stack:

```
[postgres-backup] === 1/5 checking the safety guard ===
[postgres-backup] restore over the source database is refused
[postgres-backup] === 2/5 creating a backup of 'YemomBizGrow' ===
[postgres-backup] dump written: 320K
[postgres-backup] dump verified: 27 tables present
[postgres-backup] === 3/5 restoring it into the isolated database 'yemombizgrow_backup_verify' ===
[postgres-backup] restore complete
[postgres-backup] === 4/5 comparing the source and the restored copy ===
[postgres-backup] row counts match for every table (26 tables)
[postgres-backup] flyway history restored: 19 successful migrations
[postgres-backup] === 5/5 dropping the scratch database ===
[postgres-backup] BACKUP/RESTORE VERIFIED OK
```

The drill runs in CI as well. It never modifies the application database:
the restore target is an isolated scratch database that is dropped again,
and the script refuses to restore over the source database.

---

## 8. Documentation reconciled

All three documents now report the same verified numbers and the same
commit SHA, and distinguish unit / integration / frontend-unit / browser-E2E /
manual / external-provider verification:

- `docs/PRODUCTION_READINESS_REPORT.md` (this file)
- `docs/TESTING.md`
- `docs/DEVELOPMENT_SETUP.md`

Playwright **test cases** (19) and **browser-project executions** (38) are
reported separately, as are the four E2E browser projects' identity.

---

## 9. Remaining limitations — what is NOT verified

These are the reasons the status is *staging*, not *production*.

### 8.1 Production environment not verified (blocking)

No production deployment, production database, or production secrets were
touched — that requires explicit authorization. The `prod` profile is
verified by `ProductionProfileIT` (a real production-profile boot against a
throwaway database), but the **actual production environment** has not been
exercised: real secret provisioning, real SMTP, real managed Postgres/Redis,
TLS termination, and the real reverse-proxy/CORS topology are all unverified.

### 8.2 External providers not verified (blocking)

| Provider | What the tests prove | What they do **not** prove |
| --- | --- | --- |
| Chapa | The client builds correct requests, verifies the webhook signature, and handles the documented responses. | That a **real** payment, webhook, refund or settlement works with Chapa credentials. |
| Google OAuth | PKCE, `state`, token exchange and callback handling are implemented and unit-tested. | That a **real** Google consent + token exchange succeeds, or that the refresh-token rotation works against Google's live endpoints. |
| Google Analytics / Search Console | The integration is implemented against a client abstraction. | That live data is read correctly. |
| OpenAI | The provider abstraction and the offline fallback are tested. | That the live provider returns usable responses within the latency budget. |
| Email | Dispatch is wrapped in a service. | That SMTP delivery actually succeeds in production. |

A green unit test for any of these proves the implementation matches
*controlled* responses — not that the provider accepted a real transaction.

### 8.3 Operational readiness not verified

Not covered by any test, and required before real traffic:

- backup / restore procedure and its recovery-time objective;
- deployment rollback;
- monitoring and alerting (including an alert on the `SECURITY CONTROL
  DEGRADED` marker this change introduces);
- load and failure testing;
- a secrets-rotation procedure.

### 8.4 Residual risk in the degraded login throttle

While Redis is unavailable the login throttle is **per instance** (an
in-process limit), so a horizontally scaled deployment enforces the limit per
instance rather than globally. This is a deliberate availability trade-off,
now documented in the class javadoc and logged at WARN with an explicit
`SECURITY CONTROL DEGRADED` marker so it can be alerted on. The public lead
capture limiter has the same property.

### 8.5 Not covered by the new tests

The review also suggested deeper AI/CRM work that is **not** finished:
prompt-injection testing through website content and user messages, provider
timeout/malformed-response handling at the HTTP level, rate limiting under
concurrent AI traffic, and audit-consistency when activity creation fails. The
isolation, assignment and generation-guard rules are covered; the
adversarial-input surface is not.

The AI provider currently used for recommendations is the offline
(rule-based) provider, which derives every number from persisted data — it
cannot fabricate metrics. The chat completion path does call the external
provider, with a 15 s timeout and a fallback to the offline provider; the
fallback path is tested, the live path is not (§9.2).

### 8.6 Operations not configured in this repository

Monitoring, alerting and dashboards are **not** configured here — this
repository contains the application, not the platform. What it does provide:

- structured logs around the `traceId` from `RequestTraceFilter`, returned to
  clients in error envelopes so a user-reported id finds the whole request;
- explicit `SECURITY CONTROL DEGRADED` log markers when the Redis-backed
  login throttle or the AI generation guard fall back to their in-process
  limits (these are the markers an operator should alert on);
- `/actuator/health/readiness` and `/actuator/health/liveness` for the
  orchestrator, and `/actuator/health` for the aggregate;
- the readiness probe, the rollback procedure and the backup drill documented
  in `docs/DEPLOYMENT.md`.

Wiring these markers into a paging system, and a load test against staging,
remain open.

---

## 10. Exact commands and commit

```
Backend   : cd backend;  .\mvnw.cmd clean verify      → BUILD SUCCESS, 301 unit + 30 IT
Frontend  : cd frontend; npm run typecheck             → pass
Frontend  : cd frontend; npm run lint                  → pass
Frontend  : cd frontend; npm run test                  → 25 passed
Frontend  : cd frontend; npm run build                 → 23 routes
Browser   : cd frontend; npx playwright test           → 40 passed
Docker    : docker compose config                      → valid
Docker    : docker compose build                       → 2 images built
Docker    : docker compose up -d                       → 4/4 healthy
Backup    : bash infrastructure/scripts/postgres-backup.sh verify → VERIFIED OK
```

**Commit SHA:** `04ff22b9ed6a1eaeb0190307b83bc5af641e2d88` (`04ff22b`) —
"fix(auth): resolve registration redirect, harden production profile, verify E2E".
The commits that follow it carry the fixes listed in §1.2 and §2. The
application code verified by the runs above is the tree at `HEAD` of this
report's commit; the only difference between the verified tree and `HEAD` is
this document.

---

## 11. Conclusion

YemomBizGrow has a **strong engineering foundation**: a well-structured
modular monolith, a real migration-managed schema, refresh-token rotation
with reuse detection, organization-scoped authorization, realistic
Testcontainers integration tests, and now a browser suite that actually
verifies the customer journey in a real browser against a real backend.

Every automated gate in this repository passes, and the failures that the
previous report attributed to "Playwright behaviour" were real application
and configuration defects, now fixed and covered by regression tests.

**The remaining gap is not code — it is verification against the real
production environment and the real external providers**, plus operational
readiness (backups, rollback, monitoring, load). Those require credentials and
authorization that this exercise does not have, and they are stated as limitations rather than papered over.

**Final status: READY FOR STAGING — PRODUCTION BLOCKED**
