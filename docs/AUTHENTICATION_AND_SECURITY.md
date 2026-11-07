# Authentication & Security

This document is the security model of the implemented slice. Threats are
named explicitly so future work can be checked against them.

## 1. Credentials & passwords

- Passwords hashed with **BCrypt** (cost set by Spring Security default,
  60-char hash in `users.password_hash`). Plaintext never touches disk.
- **Password policy** (`PasswordValidator`, `@StrongPassword`), enforced
  identically on the backend and mirrored by the Zod `passwordSchema` on the
  frontend (unit-tested on both sides):
  - 8–128 characters
  - at least one uppercase, one lowercase, one digit, one symbol
- Registration normalizes e-mail (trim + lowercase); `UNIQUE (email)` blocks
  racing duplicates (409).

## 2. Token model

| Token | Lifetime | Storage | Purpose |
| --- | --- | --- | --- |
| Access JWT | 15 min (`JWT_ACCESS_EXPIRATION_SECONDS=900`) | **Frontend memory only** (module variable — never `localStorage`/`sessionStorage`) | `Authorization: Bearer` on API calls |
| Refresh token | 30 days (`JWT_REFRESH_EXPIRATION_DAYS=30`) | **HttpOnly cookie** + SHA-256 digest in `refresh_tokens` | Silent session renewal |
| CSRF token | per session | Non-HttpOnly `csrf` cookie (readable by JS) + mirrored to `localStorage.YemomBizGrow_csrf` | Double-submit on refresh/logout |

- Access JWT claims: subject = user id, issued-at/expiry, `jti`. Signed with
  `JWT_SECRET` (HS256, ≥ 32 bytes entropy enforced at startup).
- The JWT carries **no organization roles** — authorization is always
  re-read from `organization_members` (see MULTI_TENANCY.md).
- The refresh token is an opaque 256-bit random string; **only its SHA-256
  hex digest is stored** (`length(token_hash) = 64` CHECK), so a database
  leak cannot be replayed.

## 3. Refresh rotation & reuse detection

```
login ──► family F (uuid) ──► token T1 (cookie)
                │
   refresh #1   ├──► T1 rotated → T2 (cookie), T1.revoked_at set, T2.replaced_by chain
   refresh #2   └──► T2 rotated → T3
                │
   replay T1 ◄── attacker/old tab ── REUSE DETECTED
                └──► revoke entire family F (T2, T3 …) → all sessions dead
```

- Every refresh **rotates** the cookie: old token is revoked, successor
  recorded via `replaced_by`.
- Presenting an already-rotated token = **reuse** →
  `RefreshTokenService` revokes the whole `family_id` and the request fails
  401. (An attacker holding a stolen cookie can use it at most once, and
  doing so kills the legitimate session too — standard anti-replay.)
- Logout revokes the family and clears both cookies (204, idempotent).

Frontend behavior (`lib/api/client.ts`):

- On 401 from an API call: **one** refresh attempt (**single-flight** per tab
  — concurrent 401s share one promise; cross-tab via **Web Locks** so two
  tabs never rotate concurrently and trip reuse detection), then **one**
  retry of the original request.
- Refresh failure → `ended` session event → context drops to anonymous and
  the guard redirects to `/login?next=…`.

## 4. CSRF (double-submit)

Cookie-authenticated endpoints are only `POST /auth/refresh` and
`POST /auth/logout`. Both require:

1. the HttpOnly refresh cookie (sent automatically), **and**
2. `X-CSRF-TOKEN` header equal to the non-HttpOnly `csrf` cookie value.

Mismatch/absence → 403 (`SecurityErrorResponder`, same error envelope).
Because the cookie is SameSite-protected *and* the header must echo it, a
cross-site attacker can neither send the cookie nor read the value.
The frontend stores the latest `csrfToken` in `localStorage.YemomBizGrow_csrf`
purely to echo it — it is useless without the HttpOnly cookie.

## 5. Login throttling (Redis, fail-open)

- Key: `login-throttle:<normalized-email>` — fixed window
  (`LOGIN_MAX_ATTEMPTS=10` per `LOGIN_WINDOW_MINUTES=15`).
- Counting happens **before** credential verification; a success clears the
  window.
- Exceeded → **429** with the uniform envelope.
- **Fail-open**: if Redis is unreachable, login proceeds (availability over
  lockout) and the failure is logged — a deliberate, documented trade-off
  for a single-backend first release (see `docs/adr/ADR-0004`).

## 6. Generic auth errors (no user enumeration)

- Login failure **always** returns the same message and status:
  `Incorrect email or password.` (401) — whether the e-mail exists or not.
- Refresh with no/invalid cookie → 403 (missing CSRF/cookie) or 401 (bad
  token); register with an existing e-mail → 409 (accepted trade-off: the
  registration form must tell the user to sign in instead).
- Invitation acceptance distinguishes 401 (token invalid/expired/used) from
  403 (token valid but bound to a different e-mail) without leaking whether
  an arbitrary e-mail is registered.

## 7. Transport & browser hardening

- Refresh cookie flags (configured, validated at startup):
  - dev: `SameSite=Lax`, `Secure=false` (localhost frontend↔backend is
    same-site)
  - prod: `SameSite=None; Secure=true` (cross-site Vercel↔Render)
  - `HttpOnly` always; `Path=/api/v1/auth`.
- CORS: **exact origins only** (`CORS_ALLOWED_ORIGINS`, comma-separated);
  a wildcard combined with credentials is rejected at startup. CORS
  responses echo the origin + `Access-Control-Allow-Credentials: true`.
- `server.error.include-stacktrace/message: never`; unexpected errors return
  only the envelope + `traceId`.
- Actuator exposes only `health`/`info`, details hidden; readiness/liveness
  probes enabled.
- Swagger/OpenAPI should be disabled in production
  (`springdoc.api-docs.enabled=false`).

## 8. Secrets management

- Base `application.yml` contains **no secret defaults** — `${JWT_SECRET}`
  etc. must be supplied by profile/env or the app fails fast
  (`StartupValidator`).
- Dev-only defaults live in `application-dev.yml` and are clearly marked;
  production profile validation rejects dev-grade secrets and
  `expose-dev-token=true`.
- `.env` is gitignored (`.env.example` committed with placeholder values).
- `NEXT_PUBLIC_*` variables are public by definition — no secrets there.

## 9. Audit logging (fire-safe)

- `AuditService` records security- and org-level actions
  (`AUTH_REGISTERED`, `ORG_ARCHIVED`, `WEBSITE_REGISTERED`, …) with actor,
  target, org, and JSONB metadata.
- **Secret scrubbing**: metadata keys matching password/token/secret/…
  are dropped before insert — audit rows never contain credentials or raw
  tokens.
- **Fire-safe**: an audit failure is logged and swallowed; it never fails
  the business transaction (availability), while the append-only DB
  constraints protect integrity.

## 10. SSRF & injection posture

- **No outbound requests are made by the backend** — there is no URL
  fetching, no webhooks, no avatar download. The classic SSRF class is
  structurally absent for this slice (see `WebsiteUrlNormalizer` docs).
  If a crawler is ever added it must re-run normalization **plus**
  DNS resolution and private-IP blocking before connecting.
- All queries go through JPA/parameter binding — no string-concatenated SQL.
- JSON body validation via Jakarta Bean Validation on every request DTO;
  service layer re-checks state (uniqueness, ownership) inside transactions.
- `open-in-view: false` — no lazy session leaks across request boundaries.

## 11. Dependency & supply-chain

- Maven Wrapper (`./mvnw`) pins plugin/dependency versions; frontend uses
  `package-lock.json` (CI runs `npm ci`).
- CI runs the full test suite on every push/PR to the respective paths
  (`.github/workflows/{backend,frontend}-ci.yml`).
- Testcontainers spins up throwaway Postgres/Redis for integration tests —
  tests never touch developer data.

## Known limitations (documented, not hidden)

1. **Redis throttle fail-open** — accepted for availability; revisit with a
   circuit-breaker + exponential backoff before large-scale launch.
2. **No e-mail verification / password reset yet** — `email_verified`
   column and `PENDING_VERIFICATION` status exist but no mail is sent;
   invitations are the only e-mail flow (dev mode logs the token).
3. **No IP binding on refresh tokens** — deliberate (privacy, mobile
   networks); reuse detection is the compensating control.
4. **Single-region single backend** — no token revocation list beyond the
   refresh-token family; a 15-min access token remains valid after logout
   (bounded by its short TTL).
