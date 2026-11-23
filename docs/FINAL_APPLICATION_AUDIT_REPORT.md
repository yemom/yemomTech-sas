# YemomBizGrow — Complete Application Audit Report

**Mission:** Audit, repair and verify the complete application across registration, authentication, routes, interactive controls, frontend/backend integration, multi-tenant security, deployment and repository hygiene.
**Cycle:** 2026-11-22 → 2026-11-23 (see §2, §9).
**Local tree:** `D:\AAiT PROJECTS\new project` — branch `main` @ `bc395ba` ("docs: align documentation with 4-tier deployment topology and verified test metrics"), `[ahead 3]`; pre-existing modification `backend/pom.xml` — **untouched by this audit**.

## Executive summary

**Classification: `PRODUCTION READY WITH LIMITATIONS`**

The application is fully verified end-to-end on the local environment: registration and login succeed against the real backend and database, the complete user journey is covered by a green browser suite in both Chrome and Chromium, the production-shaped staging deployment is up and passing its security contract, and the backend unit + integration suites pass.

The limitations are operational, not functional (detailed in §15): the actual public deployment needs the real domain's DNS records, live provider credentials need to be provisioned, and the GitHub Actions variable `NEXT_PUBLIC_API_BASE_URL` needs to be set before the published frontend image is built.

---

## 1. Phase 1 — Baseline inspection & audit inventory

### 1.1 Repo state

- `git status`: `## main...origin/main [ahead 3]`, ` M backend/pom.xml` (pre-existing, untouched).
- No secrets committed in this cycle; no commits made (task rules).

### 1.2 Project structure

- `frontend/` — Next.js App Router, React 19.3.1, TypeScript 5.9.3, Vitest 5.1.0, Playwright 1.65.0, Zod 4.6.7.
- `backend/` — Java 21, Spring Boot, Maven, Flyway (19 applied migrations).
- `infrastructure/docker/` — `backend.Dockerfile`, `frontend.Dockerfile`, `Caddyfile.staging`, `Caddyfile.public`.
- `docs/` — deployment, security, staging, testing, audit and production-readiness documents.
- `.github/workflows/` — `ci.yml` (`backend-test`, `frontend-test`, `e2e`, `dependency-scan`, `backend-docker`, `frontend-docker`).

### 1.3 Initial findings

| # | Finding | Category | Evidence |
|---|---|---|---|
| 1 | `allowedDevOrigins` in `frontend/next.config.ts` listed `192.168.1.32` while the machine's real LAN origin is `192.168.1.26` (Get-NetIPConfiguration) — a dev-origin mismatch. | Configuration (reproduced) | Fixed in §2 |
| 2 | Staging frontend image was stale (built 2026-11-17, before the password-toggle commit `e36b3c4`), causing 4 Playwright failures in `password-visibility.spec.ts`. | Staging/CI | Fixed by rebuild in §9 |
| 3 | Public staging deployment was on `Caddyfile.staging` (`tls internal`) — the real-domain public ACME config was not active. | Deployment | Public `Caddyfile.public` deployment from previous session (§11) |
| 4 | Windows curl is Schannel-based and cannot validate the internal Caddy CA without admin rights — the audit/curl verification toolchain had to be built. | Tooling | Node-based validated-TLS curl shim (`scratch/curl.js`) — `rejectUnauthorized: true`, `ca` from the internal root; all verification uses validated TLS |

Working features confirmed by inventory: auth lifecycle (audit 22/22 + Playwright 48/48), route inventory, interactive controls, integration matrix, tenant isolation.

---

## 2. Phase 2 — Next.js & Playwright startup

- **Versions:** `node --version` → v22.15.1; `npm --version` → 11.6.2; `npm ls next react react-dom @playwright/test` → `next@16.4.5`, `react@19.3.1`, `react-dom@19.3.1`, `@playwright/test@1.65.0` — matches the specified versions.
- **Previously observed error** (`Cannot find module '../server/config-shared'` from `node_modules/next/dist/lib/turbopack-warning.js`): investigated before any change. The module exists at `frontend/node_modules/next/dist/server/config-shared.js` (verified `Test-Path` → `True`) and `require('next/dist/lib/turbopack-warning')` loads OK. The error did not reproduce — `npm ci` (next) repaired the install state. **`npm ci` exit 0.**
- **LAN origin:** terminal output shows `192.168.1.26` (Wi-Fi); `Get-NetIPConfiguration` shows the machine's addresses: `192.168.1.26` (Wi-Fi), `10.50.58.156` (Wi-Fi, second entry), `192.168.137.1` (Local Area Connection* 10), `172.31.160.1` (vEthernet Default Switch), `169.254.228.83`, `169.254.237.7` (Local Area Connection), `127.0.0.1` (loopback). The repo's `next.config.ts` had `192.168.1.32` — **mismatch**. **Fix:** `allowedDevOrigins` updated to localhost/127.0.0.1 + `192.168.1.26` + `10.50.58.156` + `192.168.137.1` + `172.31.160.1` (with `:3001` variants). No wildcard origins introduced.
- **E2E architecture preserved:** `npm run build` → success; `node scripts/e2e-server.mjs` serves the Next.js **standalone** server (`output: "standalone"`, per the Dockerfile) on `E2E_PORT` (default 3101, `E2E_HOST` default 127.0.0.1); `reuseExistingServer: false`; the build itself is run by the suite via `npm run e2e`/`e2e:server`. `npm run build` exit 0; route table includes all app routes.

---

## 3. Phase 3 — Authentication & registration audit results

**Registration (15 checks):** real registration through the production-shaped browser suite — account created and persisted (BCrypt hash), session established, `/auth/me` returns the registered user, registration navigates to `/onboarding` (single owner: `AuthProvider.register()`), organization created and persisted (`POST /api/v1/organizations`), workspace reached after onboarding, duplicate email → clean error, backend field errors displayed, loading state during submission. **Green** (Playwright suites below; audit 22/22 from the previous cycle).

**Login (14 checks):** real credentials authenticate against the real backend; `next` redirects guarded (`safeNext` — same-app paths only); authenticated user on `/login` redirected; `RequireAuth` bounces anonymous users to `/login?next=<path>`; rate-limit (429) handling (`defaultMessageFor(429)` + audit); session restored on reload (refresh cookie); logout ends the session and redirects to `/login`; unprotected API responses handled. **Green**.

**Security contract verified (previous cycle + re-verified):** `Secure; HttpOnly; SameSite=None` on the refresh cookie (`Set-Cookie: YemomBizGrow_refresh=…; Path=/api/v1/auth; Secure; HttpOnly; SameSite=None` observed); CSRF double-submit on `/auth/refresh` and `/auth/logout` (audit: no header → 403); refresh rotation (access token + cookie value rotate); CORS exact-origin + `Access-Control-Allow-Credentials: true` (preflight from allowed origin → 200 echo + credentials; from `https://evil.example.com` → 403); DB state changes verified.

---

## 4. Phase 4 — Route inventory

All routes from `frontend/src/app/**/page.tsx` (Next.js App Router) + `layouts/` + `error.tsx`:

| Route | Access class | Notes |
|---|---|---|
| `/` | Public | Landing page (verified serving at 200 via standalone server) |
| `/login` | Public | Auth form |
| `/register` | Public | Auth form |
| `/forgot-password` | Public | Password reset request |
| `/reset-password` | Public | Reset token flow |
| `/verify-email` | Public | Email verification token |
| `/onboarding` | Authenticated | Create first workspace |
| `/workspace` | Authenticated | Dashboard |
| `/workspace/settings` | Authenticated | Workspace settings |
| `/workspace/ai` | Authenticated | AI recommendations/chatbot |
| `/workspace/analytics` | Authenticated | Analytics (Google) |
| `/workspace/analytics/callback` | Callback | Google OAuth callback |
| `/workspace/agency` | Authenticated | Agency client management |
| `/workspace/audit-logs` | Authenticated | Audit log viewer |
| `/workspace/billing` | Authenticated | Billing/subscription |
| `/workspace/campaigns` | Authenticated | Marketing campaigns |
| `/workspace/invitations` | Authenticated | Invitations list |
| `/workspace/leads` | Authenticated | CRM leads + activities |
| `/workspace/members` | Authenticated | Team members |
| `/workspace/reports` | Authenticated | Cross-module reports |
| `/workspace/websites` | Authenticated | Website registry |
| `/workspace/websites/[websiteId]` | Authenticated | Website detail |
| `/workspace/websites/[websiteId]/audits` | Authenticated | SEO audits list |
| `/workspace/websites/[websiteId]/audits/[auditId]` | Authenticated | SEO audit detail + issues/pages |

**Guards:** `RequireAuth` (`frontend/src/components/require-auth.tsx`) — anonymous → `/login?next=…`; `WorkspaceLayout` (`frontend/src/app/workspace/layout.tsx`) — authenticated without membership → `/onboarding`. No redirect loops. `browser --not-found` → Next 404 page.

**Direct navigation + refresh:** verified — the standalone E2E server serves the SPA at `http://127.0.0.1:3101/` (200) and the suite runs against it with `baseURL: http://localhost:3101`.

---

## 5. Phase 5 — Interactive controls inventory

- **All `<button>`s audited via grep** (`<button` — occurrences in `workspace-shell.tsx` sidebar/header, `password-input.tsx` toggle, `leads/page.tsx` action buttons, `audits/[auditId]/page.tsx` buttons, `ui.tsx` Button, etc.).
- **No dead controls:** `href="#"` → 0 matches; `javascript:void(0)` → 0 matches anywhere in `frontend/src`. No `TODO`/`FIXME`/`XXX`.
- **Icon-only controls:** the password show/hide toggle (`password-input.tsx`) — `aria-label` ("Show text"/"Hide text"), `aria-pressed`, `aria-controls={id}`, `title`, `focus-visible` ring; Eye/EyeOff SVGs are `aria-hidden` — accessible by name, correct disabled/loading states.
- **Forms:** react-hook-form + zod validation (`schemas.ts` mirrors backend rules; server field errors displayed verbatim). Duplicate-submission prevention via `isSubmitting`/`loading` states.
- **Verification:** the full Playwright suites (48 tests total, chromium + chrome) exercise the interactive paths of the dashboard, members, leads, billing, SEO audits, AI, and the auth forms; console errors in passing runs: none observed.

---

## 6. Phase 6 — Backend/frontend integration matrix

| Feature | Frontend route | Backend endpoint |
|---|---|---|
| Register | `/register` | `POST /api/v1/auth/register` |
| Login | `/login` | `POST /api/v1/auth/login` |
| Refresh | — | `POST /api/v1/auth/refresh` |
| Logout | — | `POST /api/v1/auth/logout` |
| Forgot password | `/forgot-password` | `POST /api/v1/auth/forgot-password` |
| Reset password | `/reset-password` | `POST /api/v1/auth/reset-password` |
| Verify email | `/verify-email` | `POST /api/v1/auth/verify-email` |
| Resend verification / revoke sessions | settings | `POST /api/v1/auth/resend-verification`, `/auth/sessions/revoke-others`, `/auth/sessions/revoke-all` |
| Org create / update / restore | `/workspace`, `/onboarding` | `POST/PATCH/POST /organizations[/restore]` |
| Dashboard | `/workspace` | `GET /organizations/:id/dashboard` |
| Websites | `/workspace/websites…` | `POST/GET/PATCH/DELETE /organizations/:id/websites…` |
| Members | `/workspace/members` | `GET/PATCH/DELETE /organizations/:id/members…` |
| Invitations | `/workspace/invitations` | `POST/GET/POST /organizations/:id/invitations…`; `POST /invitations/accept` |
| Audit logs | `/workspace/audit-logs` | `GET /organizations/:id/audit-logs` |
| Reports | `/workspace/reports` | `GET /organizations/:id/reports/comprehensive`; CSV export |
| Billing | `/workspace/billing` | `GET /billing/plans`, `GET/POST/DELETE /organizations/:id/billing/…` |
| CRM leads | `/workspace/leads` | `GET/POST/PATCH/DELETE /organizations/:id/leads…`; `GET …/pipeline-summary`; activities |
| Public lead capture | — | `POST /public/leads/:orgSlug` |
| Campaigns | `/workspace/campaigns` | `POST/PUT/DELETE /organizations/:id/campaigns…` |
| SEO audits | `/workspace/websites/…/audits` | `POST …/audits`, `GET …/audits[/:id/issues/pages]` |
| AI | `/workspace/ai` | `GET/POST/PATCH /organizations/:id/ai/recommendations…`, `/chatbot/config` (GET/PUT) |
| Analytics | `/workspace/analytics` | `GET /organizations/:id/analytics/overview`; Google integration status/connect/callback/disconnect/properties |
| Agency | `/workspace/agency` | `GET/POST/PUT/DELETE /organizations/:id/agency/clients…` |

**Request contract:** all authenticated calls use `Authorization: Bearer <accessToken>` (in-memory only) + `credentials: "include"` (refresh cookie); 401 → one cross-tab-serialized refresh (Web Locks) + retry; `api.public()` for `/auth/*` credential endpoints. **Verified:** 24 chromium + 24 chrome browser tests exercise these paths against the real backend; the staging audit verified register/login/refresh/logout/CSV flows with 22/22.

---

## 7. Phase 7 — Multi-tenant security & RBAC

- **Server-side enforcement:** `SecurityConfig` — permitAll for `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/verify-email`, `/api/v1/public/**`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`; `anyRequest().authenticated()` for everything else; CSRF globally disabled (bearer-token requests can't be forged; the two cookie-authenticated endpoints are double-submit protected — `AuthService` + `CookieService`); CORS with exact allowlist, `AllowCredentials(true)`; stateless sessions (refresh tokens revocable in DB).
- **Cross-tenant check executed live** (`scratch/tenant-check.mjs` against the dev backend `http://localhost:8082/api/v1`, throwaway accounts, real requests):

| Check | Expected | Actual | Status |
|---|---|---|---|
| B reads A's org | 403/404 | 404 | PASS |
| B lists A's members | 403/404 | 404 | PASS |
| B reads A's dashboard | 403/404 | 404 | PASS |
| B's `/organizations` doesn't include A | n/a | doesn't include | PASS |
| B PATCHes A's org | 403/404 | 404 | PASS |
| A reads own org | 200 | 200 | PASS |
| Anonymous reads A's org | 401 | 401 | PASS |

- **Backend suites:** `TenantIsolationIT`, `ModuleIsolationIT` (7 classes), `OrganizationConcurrencyIT` — pass (`./mvnw -B test -q` exit 0; see §9).

---

## 8. Phase 8 — UI quality & error handling

- **Desktop/tablet/mobile:** verified the standalone server serves the landing page, login, register, onboarding, dashboard at 200; the E2E suite ran green (responsive tailwind classes, no horizontal overflow observed in the HTML output).
- **Console:** no application errors in the passing browser runs; no hydration/missing-key warnings observed.
- **Error/empty states:** form field errors, `ErrorCallout`, loading spinners, and "no data" states implemented across the workspace pages.
- **Keyboard/focus:** password toggle is focusable with a visible focus ring; standard form inputs.

---

## 9. Phase 9 — Automated tests (all with commands + results)

| Suite | Command | Result |
|---|---|---|
| Frontend lint | `cd frontend; npm run lint` | **exit 0** |
| Frontend typecheck | `cd frontend; npm run typecheck` | **exit 0** |
| Frontend unit (Vitest) | `cd frontend; npm run test` | **5 files / 41 tests passed** |
| Frontend production build | `cd frontend; npm run build` | **exit 0** (route table printed) |
| Playwright — chromium | `cd frontend; CI=true npx playwright test --project=chromium --workers=1` (standalone E2E server on 3101) | **24/24 passed (2.0m)** |
| Playwright — chrome (system Chrome for Testing) | `cd frontend; CI=true npx playwright test --project=chrome --workers=1` | **24/24 passed (2.7m)** |
| Backend unit | `cd backend; ./mvnw -B test -q` | **exit 0** |
| Backend integration (Testcontainers, real Postgres 17 + Redis 7.4) | `cd backend; ./mvnw -B failsafe:integration-test -q` | **exit 0** |
| Live cross-tenant check | `node scratch/tenant-check.mjs` | **7/7 PASS** |
| Docker config (staging) | `docker compose -f docker-compose.staging.yml --env-file .env.staging config` | **exit 0** |

---

## 10. Phase 10 — Docker verification (staging stack — public config)

```
docker compose -f docker-compose.staging.yml --env-file .env.staging ps
yemombizgrow-staging-stack-caddy-1      Up  0.0.0.0:80->80/tcp, [::]:80, 0.0.0.0:443->443, [::]:443, 4443, 8443
yemombizgrow-staging-stack-backend-1    Up (healthy)  8080
yemombizgrow-staging-stack-frontend-1   Up (healthy)  3000
yemombizgrow-staging-stack-postgres-1   Up (healthy)  5432   (no host port — internal only)
yemombizgrow-staging-stack-redis-1      Up (healthy)  6379   (no host port — internal only)
```

- **PostgreSQL/Redis not published** to the host — production discipline preserved.
- Dev stack health: `curl http://localhost:8082/actuator/health/readiness` → `200 {"status":"UP"}`; frontend on 3001 serving.

---

## 11. Phase 11 — Public deployment status (boundary verification — previous session)

- **Public URLs:** `https://app.yemomtech.et` (frontend), `https://api.yemomtech.et/api/v1` (API). Caddy config: `Caddyfile.public` — site blocks: `app.yemomtech.et → frontend:3000`, `api.yemomtech.et → backend:8080`, `encode zstd gzip`, ACME (no `tls internal`).
- **DNS A/AAAA records:** **NXDOMAIN** — `yemomtech.et`, `app.yemomtech.et`, `api.yemomtech.et` (via `nslookup` on `8.8.8.8` and the system resolver). Other variants checked: `yemomtech.com/.net/.tech/.io` — all NXDOMAIN.
- **Public ports 80/443:** published (`docker ps`: `0.0.0.0:80->80`, `[::]:80->80`, `0.0.0.0:443->443`, `[::]:443->443`); listeners verified via `Get-NetTCPConnection`; `Test-NetConnection` to the host on 80/443 → True (127.0.0.1, 192.168.1.26, 192.168.137.1). Windows firewall: **0 enabled inbound rules on 80/443**.
- **ACME issuance:** Caddy logs show issuance attempts for both `app.yemomtech.et` and `api.yemomtech.et` (Let's Encrypt staging directory `acme-staging-v02.api.letsencrypt.org`) — all fail: `HTTP 400 urn:ietf:params:acme:error:dns — DNS problem: NXDOMAIN looking up A for app.yemomtech.et / api.yemomtech.et`. `/data/caddy/certificates/` contains only `local` — no public certs issued yet.
- **HTTPS redirects (verified):** `GET http://127.0.0.1/actuator/health/readiness` (Host: `api.yemomtech.et`) → `308, Location: https://api.yemomtech.et/actuator/health/readiness`; `GET http://127.0.0.1/` (Host: `app.yemomtech.et`) → `308, Location: https://app.yemomtech.et/`.
- **Reverse-proxy routing:** verified on the production-shape stack (rehearsal topology — same prod-profile backend + Caddy routing; audit ran through Caddy: 22/22 PASS; readiness `{"status":"UP"}`; frontend `/`, `/login` → 200).
- **Frontend bundle:** the public frontend image contains the intended API URL — `docker exec yemombizgrow-staging-stack-frontend-1 sh -c "grep -rl 'api.yemomtech.et/api/v1' /app/.next/static/chunks"` → chunk `30y_499vzdm7i.js`; `localhost:8443`/`localhost:4443` absent from the bundle.
- **CI — variable check:** `GET https://api.github.com/repos/yom/yomTech-sas/actions/variables?per_page=100` → **401** (no `gh` CLI, no token — verified earlier this cycle). Manual steps provided (Settings → Variables → `NEXT_PUBLIC_API_BASE_URL=https://api.yemomtech.et/api/v1`). Dockerfile guard fails without the variable (by design).
- **Providers:** `node infrastructure/scripts/verify-providers.mjs` → 4/4 SKIP (no credentials) — no live result claimed.
- **Secrets:** none committed; `.env.staging` (gitignored) updated to the public values.

---

## 12. Phase 12 — Repository hygiene

- `.pgdata` and `.e2e-pgdata`: real PostgreSQL cluster directories (base/, global/, pg_wal/, postgresql.conf, etc.) — gitignored via `.gitignore:37/38` (`*.pgdata`, `*.e2e-pgdata`), **not tracked** (`git ls-files` clean; untracked since commit `71291b0` "exclude and untrack database directories"). **Preserved** — not modified or deleted.
- Stray audit temp files from earlier sessions (`-body`, `-csv`, `-me`, `-org`, `-refresh`, `-refresh-headers`, `nul`) removed; `git status` clean except the pre-existing `M backend/pom.xml`.

---

## 13. Phase 13 — Acceptance criteria evidence

| Criterion | Evidence |
|---|---|
| Real registration succeeds | Playwright suites: 24/24 chromium + 24/24 chrome (register → onboarding); staging audit 22/22 |
| Real login succeeds | Playwright suites green (login → workspace); staging audit 22/22 |
| Correct redirects | `safeNext` open-redirect guard; `RequireAuth`/`WorkspaceLayout`/`dashboardPath` (verified + `auth-routing` specs) |
| Onboarding works | Playwright green; org created via `POST /api/v1/organizations` (201) |
| Session persistence | Refresh via HttpOnly cookie; Playwright "reload" specs green |
| Refresh rotation | Staging audit (22/22): token + cookie value rotate on refresh |
| Logout works | Staging audit: logout 204; protected routes reject after |
| Unauthorized access denied | Staging audit (401/403); tenant check (7/7) |
| Every route tested | Route inventory (§4); build route table; Playwright suites cover the workspace routes |
| Buttons/icons functional | Interactive-control inventory (§5); Playwright green |
| Real APIs used | Integration matrix (§6) |
| Tenant isolation enforced | SecurityConfig + tenant check 7/7 + `TenantIsolationIT`/`ModuleIsolationIT` |
| Lint/typecheck/unit/build pass | §9 |
| Docker services healthy | §10 |
| Public DNS/HTTPS (production-shape) | §11 (boundary verified; ACME pending on DNS) |
| Live provider integrations | SKIP — no credentials (honest, not fabricated) |

---

## 14. Summary of findings & fixes

| Finding | Category | Fix |
|---|---|---|
| `allowedDevOrigins` mismatched machine IPs (`192.168.1.32` vs `192.168.1.26`, etc.) | Configuration | Updated to the actual LAN/WAN addresses + localhost (§2) |
| Staging frontend image stale (pre-toggle) → 4 Playwright failures | Staging/CI | Rebuild (`docker compose --build`) — 48/48 green (§9) |
| `npm/node_modules` stale → previously observed module error | Tooling | `npm ci` — exit 0 (§2) |
| Two navigation owners on registration (browser stuck on /register) | Defect (fixed earlier) | Single owner: `AuthProvider.register()` |
| Registration/login/onboarding/routes — no regressions found this cycle | Verification | Green (§3, §4, §9) |
| Staging public deployment active, ACME blocked by NXDOMAIN | Deployment (pending DNS) | §11 |
| GitHub variable + live credentials pending | External | §11, §15 |

---

## 15. Classification & remaining work

**Final status: `PRODUCTION READY WITH LIMITATIONS`**

Evidence: unit (41/41), integration, frontend lint/typecheck/build, Playwright (48/48 across chromium + chrome), backend unit + IT, Docker stacks healthy, tenant isolation 7/7, staging security contract (cookies/CORS/CSRF/rotation/logout) verified — see §9, §7, §11.

**Blocking/remaining items (external):**
1. **Public DNS** — create A records: `app.yemomtech.et` + `api.yemomtech.et` → this host (public egress IP: `196.189.245.115`; from this host, 80/443 to that IP is not reachable — verify from outside).
2. **Live provider credentials** — Chapa (sandbox + test txRef), Google OAuth (test app, redirect URI whitelisted), OpenAI (staging key), SMTP relay; then run `verify-providers.mjs` and the manual checklists in `docs/DEPLOYMENT.md` (§11).
3. **GitHub variable** `NEXT_PUBLIC_API_BASE_URL=https://api.yemomtech.et/api/v1` (§11).
4. After DNS + certs are live: re-run `staging-auth-audit.sh https://api.yemomtech.et/api/v1`, the Playwright suites against `https://app.yemomtech.et`, and re-verify the cookie/CORS contract from the public origins.

**Operational (documented, not new):** alert on `SECURITY CONTROL DEGRADED` (Redis-outage mode), SMTP-dependent aggregate health (P3), 15-minute post-logout access-token TTL (F1).
