# Testing

Four layers, each catching a different class of defect. Every gate runs in CI
(`.github/workflows/backend-ci.yml`, `frontend-ci.yml`, `ci.yml`).

| Layer | Tool | Test cases | Command |
| --- | --- | --- | --- |
| Backend unit | JUnit 5 + Mockito + AssertJ | **301** (28 classes) | `cd backend; .\mvnw.cmd test` |
| Backend integration | Spring Boot IT + **Testcontainers** (Postgres 17, Redis) | **30** (5 test classes) | `cd backend; .\mvnw.cmd verify` |
| Frontend unit | Vitest 5 + Testing Library (jsdom) | **25** (3 files) | `cd frontend; npm run test` |
| Frontend E2E | **Playwright 1.65** (Chrome + Chromium, real backend) | **20** cases → **40** executions | `cd frontend; npm run e2e` |

Full backend gate: `.\mvnw.cmd -B clean verify` → **BUILD SUCCESS, 331 tests, 0
failures** (301 unit + 30 integration).

> The counts above are read from the generated Surefire/Failsafe XML reports
> (`backend/target/surefire-reports`, `backend/target/failsafe-reports`) — they
> are not estimated.

> **Test cases vs. executions.** Playwright runs every test case once per
> browser project, so 20 test cases produce 40 executions (20 in `chrome`,
> 20 in `chromium`). Both numbers are reported separately throughout the
> documentation so a "40 passed" can never be mistaken for 40 distinct
> scenarios.

## Backend unit tests (fast, no I/O)

Located under `backend/src/test/java/…` next to the code they cover:

- `PasswordValidatorTest` — all six password rules, boundaries (8/128).
- `SlugGeneratorTest` — slugify, uniqueness suffixes, Unicode names.
- `WebsiteUrlNormalizerTest` — scheme/host normalization **and every SSRF
  rejection**: localhost, loopback/private/link-local IPs, credentials,
  non-http schemes, trailing dots.
- `TokenHasherTest` — SHA-256 hex, 64-char invariant.
- `PermissionTest` — the permission matrix (every role × permission,
  owner-only `ORG_DELETE`, future-module grants).
- `SecurityProfileModeTest` — the production security boundary: `prod` and
  `production` are always strict, `dev`/`test`/`local` may relax, and a mixed
  `prod`+`dev` (or `prod`+`test`, `prod`+`local`) profile set is **rejected**.
- `StartupValidatorTest` — the validator's behaviour per profile: dev keeps
  the documented placeholders, production rejects them, production refuses to
  boot with the API documentation enabled.
- `LoginThrottleServiceTest` — window counting, success clears the window,
  and the **Redis-outage policy**: degradation to a conservative in-process
  limit, recovery once Redis answers, concurrency, and a bounded counter map.
- `LeadServiceTest` — lead lifecycle plus the assignment rule (a lead can
  only be assigned to an active member of the workspace).
- `BillingServiceTest` — the Chapa webhook contract: signature validation,
  server-side verification, amount/currency/reference mismatch, replay
  protection and **state-downgrade protection** (an out-of-order webhook can
  never cancel a paid subscription).
- `SsrfAdversarialTest` — the crawler's SSRF defence against address
  spelling tricks: IPv6 forms that embed an IPv4 address (`::127.0.0.1`,
  6to4, Teredo), decimal/octal/hex loopback hosts, embedded credentials, a
  redirect into a private network (every hop is re-validated) and an
  oversized response.
- `AiServiceTest` — the recommendation contract: the GET endpoint is a pure
  read, generation is rate limited and mutually exclusive, and success and
  failure are audited.
- plus DTO/service edge cases (validation messages, state transitions).

Rules: no database, no network; Mockito for collaborators; AssertJ
assertions; display names describe behavior, not methods.

## Backend integration tests (Testcontainers)

`@SpringBootTest` + real Postgres/Redis containers, full context including
security, Flyway, and CORS:

### `VerticalSliceIT` (13 tests, ordered)
The product's critical path, executed exactly as a user would:

1. register → `/me` (session established, cookie set);
2. create organization → ET/ETB/Addis defaults + slug;
3. website registration → URL normalization + per-org domain uniqueness
   (duplicate → 409);
4. dashboard reflects persisted state;
5. **refresh rotation** — old cookie replayed → **family revocation**;
6. **CSRF required** on cookie endpoints (403 without header);
7. logout clears cookies and permanently ends the family;
8. invitations: issue (dev token) → accept → duplicate/foreign rejection;
9. members: role change, last-owner protection, self-edit forbidden;
10. website soft-deactivation (row survives as `INACTIVE`, idempotent);
11. **PATCH organization: empty string clears optional fields**, invalid
    URL → 400 envelope (regression test for a bug found in UI testing);
12. archive: owner-only, 204, subsequent access → 404.

Every negative assertion uses `assertErrorEnvelope` — status **and**
`timestamp/status/error/message/path/fieldErrors/traceId` shape.

### `TenantIsolationIT` (3 tests)
Two users × two organizations: cross-org PATCH → 404 (no existence leak),
cross-org member operations → 404, privilege escalation → 403, last-owner
demotion → 409.

### `ModuleIsolationIT` (7 tests)
Tenant isolation for every organization-scoped module (not just the core).
Each module is checked with the same shape: the data is created in
organization A and then read, modified and deleted as a user of organization
B — all attempts must be refused, while the owning workspace keeps working.

- AI recommendations are scoped to the caller's own workspace — a second
  workspace gets 403/404 and never sees the foreign data;
- the chatbot configuration is scoped for both reading and writing;
- CRM leads are scoped: list, read, update and pipeline transitions all
  refuse a foreign lead;
- a lead cannot be assigned to a user outside the organization;
- websites are scoped: list, read, update and delete all refuse;
- SEO audits are scoped: neither listing nor triggering a foreign audit works;
- billing, campaigns, analytics, reports (including the CSV export),
  members, invitations, audit logs and agency client relationships are all
  refused for a foreign workspace.

### `OrganizationConcurrencyIT` (3 tests)
Organization creation is a three-step transaction (slug → insert → owner
membership) and the slug is chosen by a read-then-write sequence, so
concurrent creations of the same name can collide:

- concurrent creates of the same name never produce a duplicate slug — the
  database constraint `uq_organizations_slug` is the final authority and the
  loser gets a clean **409**, never a 500;
- every organization has exactly one owner membership (**no orphan rows**,
  i.e. a partial create is always rolled back);
- slugs stay unique across many organizations sharing a name.

### `ProductionProfileIT` (4 tests)
Boots the whole application with the **production** profile:

- the OpenAPI description is not published (`/v3/api-docs` → 404);
- the Swagger UI is not published (`/swagger-ui.html`, `/swagger-ui/index.html`
  → 404);
- protected endpoints still require authentication;
- the readiness and liveness probes stay reachable.

### Conventions
- `AbstractIntegrationTest` provides `get/post/put/patch/delete`,
  `bearerHeaders`, `jsonPath`, `assertStatus`, `assertErrorEnvelope` —
  tests read as HTTP scripts.
- Fresh data per class via unique e-mails/slugs; containers are
  ephemeral — **tests never touch developer data**.
- `application-test.yml` + Testcontainers disable external dependencies.

## Frontend unit tests (Vitest)

- `src/lib/api/schemas.test.ts` — password policy (mirrors backend),
  login/register/website/organization/invite schemas, boundary values.
  **Pinning the mirror**: if the frontend rules drift from the backend
  documented rules, these fail.
- `src/lib/api/permissions.test.ts` — the permission matrix mirrored from
  `Permission.java` (owner-only archive, admin-only member management,
  marketing-manager website access, invitable roles exclude owner).
- `src/lib/api/api-base-url.test.ts` — **configuration guard**: the API base
  URL fallback must be identical in `next.config.ts`, the API client,
  `.env.example`, `frontend/.env.example` and `docker-compose.yml`, and must
  never point at the retired port 8082. This test exists because a stale
  fallback once made the frontend boot cleanly and talk to a backend that was
  not running.
- Setup: `src/test/setup.ts` (`@testing-library/jest-dom/vitest`),
  jsdom environment, `@` alias configured in `vitest.config.ts`.

Run with coverage: `npm run test -- --coverage` (v8 provider, text+lcov).

## End-to-end tests (Playwright)

20 test cases in 8 spec files, executed in **both** browser projects
(`chrome` = real Google Chrome, `chromium` = bundled Chromium) → **40
executions**.

| Spec | Cases | Covers |
| --- | --- | --- |
| `vertical-slice.spec.ts` | 4 | full owner journey, generic login error, client-side password policy, unauthenticated redirect |
| `auth-routing.spec.ts` | 10 | the routing rules (new user → onboarding, existing user → workspace, authenticated visitor on `/login`/`/register`, `next` handling, open-redirect rejection, **recovery from a failed `/auth/me` after registration**, duplicate registration, reload, logout) |
| `agency-lifecycle.spec.ts` | 1 | agency client linking, fail-closed on an unknown slug |
| `ai-chatbot.spec.ts` | 1 | AI insights & bot surface |
| `billing.spec.ts` | 1 | subscription & billing surface (ETB context) |
| `crm-leads.spec.ts` | 1 | CRM pipeline + lead creation (fills the form by accessible label) |
| `multi-tenant-isolation.spec.ts` | 1 | two accounts, two workspaces, no cross-visibility |
| `seo-audit.spec.ts` | 1 | website registration **and a real audit**: the audit is triggered through the UI and the persisted row is read back from the API |

### The server under test

Playwright serves the frontend from a **production build**
(`next build` + the standalone server, exactly the artifact Docker ships) —
never `next dev`.

This is not a preference, it is a correctness requirement:

- the dev server compiles routes **on demand**, so the first visit to a route
  can take many seconds. A click that lands before React has hydrated becomes
  a native HTML form submission, and a navigation that outlasts the
  assertion timeout fails while the application is in fact working;
- a production build has every route pre-compiled (23 routes) and hydrates
  immediately, so the suite is deterministic and tests what users get.

The E2E server runs on its **own port (3101)** with
`reuseExistingServer: false`. The development server (3001) and the Docker
frontend (3001) can stay running — the suite never reuses either of them,
because reusing "whatever happens to be listening" is how a test run ends up
proving nothing.

Prerequisites: the backend on **8080** (dev profile) with CORS allowing the
E2E origin (`http://localhost:3101`), and a browser installed via
`npx playwright install chromium` (Google Chrome must be installed for the
`chrome` project). On failure Playwright saves a screenshot, a page snapshot
and a video under `test-results/`.

## Manual browser verification (performed)

In addition to automated tests, the full slice was walked in a real browser
against the running stack — this is how the following bugs were found and
fixed (each now covered above):

| Bug | Fix |
| --- | --- |
| Login/register didn't store the returned session → `/auth/me` 401 | `setSession()` after `api.public` |
| Strict-mode double-effect stuck on "Checking your session…" | removed bootstrap ref guard (refresh is single-flight) |
| Register auto-redirect raced onboarding redirect | removed the redundant redirect |
| No-org user stuck on "Loading workspace…" (disabled query = pending forever) | workspace layout redirects to `/onboarding` |
| Backend `@Pattern` rejected `""` although javadoc says "clears" | DTO regex allows empty; IT added |
| `asChild` misuse on members page | `LinkButton` |
| setState-in-effect lint error (React 19 rule) | popover close moved to click handlers |
| **Two navigation owners after registration** (provider `router.push` + page `window.location.href`) — the full-page load cancelled the in-flight client-side transition, so the browser stayed on `/register` | single owner: the provider navigates, the page does not |
| **E2E ran against the on-demand-compiling dev server** — clicks landed before hydration (native form submit) and route compilations outlasted the assertion timeout | E2E runs against a production build |
| **Concurrent organization creation returned 500** — JPA defers the INSERT to commit, outside the `try/catch` that converts it to a conflict | `saveAndFlush` inside the `try` |
| **A lead could be assigned to a user outside the organization** | assignee must be an active member (`LeadService.requireActiveMember`) |
| **Aggregate health reported DOWN in dev** because no SMTP server is configured | mail health indicator disabled in the dev profile only |
| **Frontend container was unhealthy** — the health check used `localhost`, which resolved to `::1` while the server binds IPv4 only | health checks use `127.0.0.1` |

## Adding tests — checklist

- [ ] New business rule → unit test at the service (happy + boundary +
      failure paths).
- [ ] New endpoint → extend `VerticalSliceIT` (or a new ordered IT class):
      2xx shape **and** envelope shape of the failure modes (400/401/403/
      404/409).
- [ ] New permission → update `PermissionTest` **and**
      `frontend/permissions.test.ts` together.
- [ ] New validation rule → backend DTO test + mirrored Zod schema test.
- [ ] New tenant-isolation rule → `ModuleIsolationIT`.
- [ ] New user journey → Playwright spec (serial, unique e-mails, no
      test-data collisions).
- [ ] Run the full gates before pushing: `.\mvnw.cmd -B clean verify`,
      `npm run typecheck && npm run lint && npm run test && npm run build`,
      `npm run e2e`.
