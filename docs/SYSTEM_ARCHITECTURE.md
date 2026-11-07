# System Architecture

YemomTech (YemomBizGrow) is a **modular monolith**: a single Spring Boot deployable
whose packages are modules with explicit boundaries, paired with a Next.js
frontend. This document describes the runtime structure; security details live
in [AUTHENTICATION_AND_SECURITY.md](AUTHENTICATION_AND_SECURITY.md), tenant
isolation in [MULTI_TENANCY.md](MULTI_TENANCY.md).

## High-level view

```
 Browser (React 19 / Next.js 16, port 3001 in dev)
   │  HTTPS + Bearer access token (in memory only)
   │  Refresh cookie: HttpOnly, SameSite=Lax (dev) / None+Secure (prod)
   ▼
 Spring Boot 3.5.16 (Java 21, port 8082 dev / 8080 default)
   ├─ common/security   JWT filter, SecurityConfig, error responder
   ├─ common/exception  GlobalExceptionHandler → uniform error envelope
   ├─ common/config     CORS, request-trace filter, startup validation
   ├─ auth              register/login/refresh/logout/me, throttling
   ├─ user              user records
   ├─ organization      orgs, dashboard, slug lifecycle
   ├─ membership        roles, permission checks, member management
   ├─ invitation        hashed tokens, 7-day expiry, accept flow
   ├─ website           URL normalization, per-org uniqueness
   └─ audit             fire-safe append-only trail
   ▼
 PostgreSQL 17 (Flyway V1–V7, `ddl-auto: validate`)   Redis 7 (throttling)
```

Development compose (`docker-compose.yml`) runs all four services; production
runs the backend and frontend as separate services against managed
PostgreSQL/Redis (see [DEPLOYMENT.md](DEPLOYMENT.md)).

## Module rules

- Each module owns its package: `com.YemomBizGrow.<module>` with `dto/` subpackage.
- **Entities never cross the wire** — controllers accept/return DTO records
  only; mapping happens in the module's service.
- Cross-module reads go through the target module's service
  (e.g. `OrganizationReadService.findByIds`), never its repository directly.
- Constructor injection everywhere; no field injection, no `@Autowired`.
- Controllers are thin: validate → delegate → map. Business rules live in
  services. Transactions are declared at the service boundary.

## Request lifecycle

1. **`RequestTraceFilter`** assigns a `traceId` (propagated as `X-Trace-Id`)
   so every log line and error envelope for a request correlate.
2. **`JwtAuthenticationFilter`** (stateless) parses `Authorization: Bearer …`,
   validates signature/expiry, and populates the `SecurityContext`. Absent or
   invalid token → the request continues unauthenticated; endpoint rules in
   `SecurityConfig` decide whether that is allowed.
3. **Controller** validates the DTO (`@Valid`), resolves the current user via
   `CurrentUser`.
4. **Access control**: `AccessControlService.requirePermission(...)` re-reads
   the membership row (the only authorization source) and enforces
   404-for-non-members / 403-for-missing-permission.
5. **Service** executes the business rule inside a transaction and writes an
   audit entry through `AuditService` (fire-safe: audit failures never fail
   the business operation).
6. **Response** is a DTO; collections use `PageResponse` (Spring Data `Page`
   serialization: `content`, `page`, `size`, `totalElements`, `totalPages`,
   `first`, `last`).

## Uniform error envelope

Every non-2xx response — validation, security, business, and unhandled — is
the same shape, produced by `GlobalExceptionHandler` /
`SecurityErrorResponder`:

```json
{
  "timestamp": "2026-11-07T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed.",
  "path": "/api/v1/auth/register",
  "fieldErrors": { "email": "Email is not valid." },
  "traceId": "0f1e…"
}
```

- `fieldErrors` is present only for bean-validation failures (400).
- `traceId` is always present; stack traces and internal messages are never
  exposed (`server.error.include-stacktrace: never`).

### Status-code conventions

| Code | Meaning | Example |
| --- | --- | --- |
| 400 | Validation / malformed input | bad email, invalid URL, over-long field |
| 401 | No/expired session | missing bearer token, bad refresh cookie |
| 403 | Authenticated but not allowed | member lacking the permission; wrong CSRF |
| 404 | **Not visible to this caller** | non-member requests a tenant resource |
| 409 | State conflict | duplicate slug/domain/invitation |
| 429 | Throttled | login attempts over the Redis window |
| 500 | Unexpected (with traceId only) | anything unhandled |

## Configuration model

- `application.yml` — shared, **no secret defaults**: unresolved placeholders
  (`${JWT_SECRET}` etc.) force the environment/profile to supply values;
  `StartupValidator` refuses to boot with dev-grade config in production.
- `application-dev.yml` — local convenience defaults (dev CORS, dev JWT
  secret, `expose-dev-token` allowed).
- `application-test.yml` — integration-test settings (Testcontainers).
- Environment variables are documented in `.env.example` and
  [DEVELOPMENT_SETUP.md](DEVELOPMENT_SETUP.md).

## Frontend architecture

- **App Router** (`src/app/`): public pages (`/`, `/login`, `/register`),
  `/onboarding` (first workspace), `/workspace/*` (shell + dashboard,
  websites, members, invitations, settings).
- **`lib/api/client.ts`** — single HTTP client: access token held in module
  memory (never `localStorage`), CSRF value mirrored to
  `localStorage.YemomBizGrow_csrf`, 401 → one refresh (single-flight per tab,
  Web Locks across tabs) → one retry, `ended` session event → anonymous.
- **`lib/api/hooks.ts`** — TanStack Query hooks (query keys namespaced per
  org, mutations invalidate narrowly).
- **`lib/auth/auth-context.tsx`** — session bootstrap (`/auth/login` or
  refresh → `/auth/me`), login/register/logout, current-org selection
  (persisted in `localStorage.YemomBizGrow.current_org`, validated against live
  memberships on every boot).
- **`components/require-auth.tsx`** — route guard: unauthenticated →
  `/login?next=<path>` (same-app paths only, open-redirect guard).
- **Zod schemas** (`lib/api/schemas.ts`) mirror backend validation rules;
  unit tests pin the two implementations to the same behavior.

## Deliberate non-goals (this slice)

- **No outbound HTTP from the backend** — website registration stores
  normalized metadata only. Any future crawler must re-run
  `WebsiteUrlNormalizer` plus DNS/private-IP checks first (documented in
  `WebsiteService`).
- **No payments, no AI inference, no analytics numbers** — nothing is faked;
  the dashboard shows only persisted counts.
- **No org restore flow** — archive is one-way for now (see ROADMAP).
