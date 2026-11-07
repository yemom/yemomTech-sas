# Testing

Three layers, each catching a different class of defect. All gates are
wired into CI (`.github/workflows/backend-ci.yml`,
`frontend-ci.yml`).

| Layer | Tool | Count (verified) | Command |
| --- | --- | --- | --- |
| Backend unit | JUnit 5 + Mockito + AssertJ | **100** | `cd backend; .\mvnw.cmd test` |
| Backend integration | Spring Boot IT + **Testcontainers** (Postgres 17, Redis) | **16** (2 test classes) | `cd backend; .\mvnw.cmd verify` |
| Frontend unit | Vitest 5 + Testing Library (jsdom) | **21** (2 files) | `cd frontend; npm run test` |
| Frontend E2E | **Playwright 1.65** (Chromium, real backend) | **4** | `cd frontend; npm run e2e` |

Full backend gate: `.\mvnw.cmd -B verify` → **BUILD SUCCESS, 116 tests, 0
failures** (latest run).

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
- `LoginThrottleServiceTest` — window counting, success clears, Redis
  fail-open behavior (mocked Redis).
- plus DTO/service edge cases (validation messages, state transitions).

Rules: no database, no network; Mockito for collaborators; AssertJ
assertions; display names describe behavior, not methods.

## Backend integration tests (Testcontainers)

`@SpringBootTest` + real Postgres/Redis containers (reused across tests in
one class), full context including security, Flyway, and CORS:

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

### Conventions
- `AbstractIntegrationTest` provides `get/post/patch/delete`,
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
- Setup: `src/test/setup.ts` (`@testing-library/jest-dom/vitest`),
  jsdom environment, `@` alias configured in `vitest.config.ts`.

Run with coverage: `npm run test -- --coverage` (v8 provider, text+lcov).

## End-to-end tests (Playwright)

`frontend/e2e/vertical-slice.spec.ts` — real Chromium against the **real
backend (8082)** and the dev server (3001), serial (`workers: 1`) because
tests share one backend:

1. **owner runs the full vertical slice** — register → onboarding →
   create workspace → dashboard shows real counts/OWNER/ETB/Addis →
   register website with messy input (`HTTPS://WWW.E2e-Example.COM/` →
   normalized `www.e2e-example.com` + ACTIVE) → members (`(you)`,
   self-suspend/remove disabled) → logout → protected page bounces →
   login returns to workspace via `next`;
2. **generic login error** — wrong password → exact backend message, no
   enumeration, stays on `/login`;
3. **password policy client-side** — weak password → Zod message, no
   request sent;
4. **unauthenticated redirect** — `/workspace/websites` →
   `/login?next=%2Fworkspace%2Fwebsites`.

Prerequisites: backend running on 8082 (dev profile) + `npm run dev` on
3001 (the config reuses an already-running dev server), Chromium
installed via `npx playwright install chromium`. On failure Playwright
saves a screenshot + page snapshot under `test-results/`.

## Manual browser verification (performed)

In addition to automated tests, the full slice was walked in a real
browser against the running stack — this is how the following bugs were
found and fixed (each now covered above):

| Bug | Fix |
| --- | --- |
| Login/register didn't store the returned session → `/auth/me` 401 | `setSession()` after `api.public` |
| Strict-mode double-effect stuck on "Checking your session…" | removed bootstrap ref guard (refresh is single-flight) |
| Register auto-redirect raced onboarding redirect | removed the redundant redirect |
| No-org user stuck on "Loading workspace…" (disabled query = pending forever) | workspace layout redirects to `/onboarding` |
| Backend `@Pattern` rejected `""` although javadoc says "clears" | DTO regex allows empty; IT added |
| `asChild` misuse on members page | `LinkButton` |
| setState-in-effect lint error (React 19 rule) | popover close moved to click handlers |

## Adding tests — checklist

- [ ] New business rule → unit test at the service (happy + boundary +
      failure paths).
- [ ] New endpoint → extend `VerticalSliceIT` (or a new ordered IT class):
      2xx shape **and** envelope shape of the failure modes (400/401/403/
      404/409).
- [ ] New permission → update `PermissionTest` **and**
      `frontend/permissions.test.ts` together.
- [ ] New validation rule → backend DTO test + mirrored Zod schema test.
- [ ] New user journey → Playwright spec (serial, unique e-mails, no
      test-data collisions).
- [ ] Run the full gates before pushing: `.\mvnw.cmd -B verify`,
      `npm run typecheck && npm run lint && npm run test && npm run build`,
      `npm run e2e`.
