# YemomBizGrow — Staging Deployment Execution & Final Release Gate

**Date:** 17 November 2026 · **Base:** `beb278e` (the e2e API-base fix)

## What changed vs. the previous audit

The previous report (`FINAL_STAGING_AUDIT.md`) classified the app
**READY FOR STAGING — PRODUCTION BLOCKED**, with three external blockers.
This cycle **executed the staging deployment itself** (the exact
production topology, run locally because no public domain is available
from this machine) and re-verified everything over **real HTTPS**.

## 1. Deployment environment & verified URLs

| Endpoint | URL (this rehearsal) | Verified |
| --- | --- | --- |
| Frontend (app) | `https://localhost:4443` | 200 over validated TLS |
| Backend (API) | `https://localhost:8443/api/v1` | readiness 200 over validated TLS |
| Internal DB / Redis | project-network only | **not published to the host** |

Stack: `docker-compose.staging.yml` (new, committed) — prod-profile
backend, frontend built with the **public API URL baked in** (verified:
the served chunks contain exactly `https://localhost:8443/api/v1`, zero
`localhost:8080`), Caddy TLS termination, isolated Postgres/Redis/volumes.
All secrets are *required* variables (`${VAR:?}` — the file refuses to
start with development defaults); real values live in the gitignored
`.env.staging`.

**HTTPS cookie verification (the part B2 existed to prove):**
`Set-Cookie` over validated TLS from the prod profile:
`YemomBizGrow_refresh=…; Path=/api/v1/auth; Secure; HttpOnly; SameSite=None`
— exactly the production contract. `Secure`+`SameSite=None` is now a
**locally verified** behavior, no longer an assumption.

**Exact-origin credentialed CORS over TLS:** preflight from
`https://localhost:4443` → 200 with `Access-Control-Allow-Credentials:
true` and the origin echoed exactly; from `https://evil.example.com` →
**403**. No wildcard.

**Prod hardening over TLS:** `/v3/api-docs` → 404, `/actuator/env` →
401, `/auth/me` unauthenticated → 401.

## 2. GitHub Actions configuration status

Verified in `.github/workflows/ci.yml`:

- `frontend-docker` passes `NEXT_PUBLIC_API_BASE_URL=${{
  vars.NEXT_PUBLIC_API_BASE_URL }}` as a build-arg; the Dockerfile guard
  fails the build if the variable is unset (no silent localhost).
- Image jobs are gated behind `[backend-test, frontend-test, e2e,
  dependency-scan]` and run only on `main` pushes.
- No provider secrets are required by CI (only the built-in
  `GITHUB_TOKEN` and the optional `NVD_API_KEY`).

**Repository variable `NEXT_PUBLIC_API_BASE_URL`: could not be checked
programmatically — no `gh` CLI on this machine and no API token.**
Configuration instructions (exact):

1. GitHub → repository `yemom/yemomTech-sas` → **Settings** →
   **Secrets and variables** → **Actions** → **Variables** tab →
   **New repository variable**.
2. Name: `NEXT_PUBLIC_API_BASE_URL`
   Value: the real public backend URL, e.g. `https://api.yemomtech.et/api/v1`
   (must match the API host users reach; never localhost).
3. Re-run the `frontend-docker` job (or push to `main`) and confirm the
   built image's chunks contain that URL — the Dockerfile fails
   explicitly if the variable is missing.

## 3. Authentication staging test results

| Suite | Target | Result |
| --- | --- | --- |
| `staging-auth-audit.sh` (22 raw-HTTP checks) | `https://localhost:8443/api/v1` (TLS-validated curl) | **22/22 PASS** |
| Playwright full suite (22 scenarios, chromium) | `https://localhost:4443` through Caddy → prod backend | **22/22 PASS** |

Covered: registration → onboarding → workspace access, session
persistence across reload, logout + protected-route rejection,
open-redirect rejection, refresh rotation + CSRF enforcement (403
without header), RBAC denial of foreign-organization data, authenticated
CSV export (200 `text/csv`) and unauthenticated denial (401), generic
error envelopes. Isolated throwaway accounts were used against the
staging database.

## 4. Provider integration test results

`verify-providers.mjs`: **4/4 SKIP** (no credentials available). No
integration is claimed to work. Chapa / Google / OpenAI / SMTP remain
**UNVERIFIED against live services**; the manual checklists in
`DEPLOYMENT.md` are the path once credentials exist.

## 5. Security findings

| ID | Severity | Finding | Action taken / remediation |
| --- | --- | --- | --- |
| S1 | **P2 — fixed** | The dev `docker-compose.yml` inlines an AES key (`Eyds…`), and `StartupValidator`'s "known dev key" blocklist contained only the *other* dev default — so the committed key would have been **accepted in a strict profile**. | Added it to the blocklist (`KNOWN_DEV_ENCRYPTION_KEYS`); `StartupValidatorTest` 13/13 PASS including two new cases (prod rejects, dev tolerates). Cannot be deleted from dev compose without re-encrypting existing local dev data — now explicitly commented DEV-ONLY. |
| S2 | INFO | CI's `frontend-test` gate build uses the placeholder `https://api.example.com/api/v1`. | Harmless: that artifact is never published; the released image is built by `frontend-docker` with the real repository variable. |
| S3 | INFO | Compose dev defaults include dev-only JWT/db credentials, clearly labelled. | Accepted for development; production never uses `docker-compose.yml` (platform secret store, per `DEPLOYMENT.md`). |
| — | — | Secrets committed | None found beyond S1 (`git grep` for key/password/CERTIFICATE patterns; `.env*`, `*.pem`, `*.key` untracked; staging secrets gitignored). |
| — | — | Production localhost URLs | None in `application-prod.yml` or published images (bundle greps clean; build refuses without the variable). |
| — | — | Wildcard credentialed CORS | None; `StartupValidator` forbids it and live preflight showed exact-origin enforcement (403 for evil origin). |
| — | — | Swagger/management exposure | springdoc 404, `/actuator/{env,metrics}` 401 over TLS. |
| — | — | Sensitive logging | No log statements touching password/token/cookie values in backend main code. |
| — | — | Migrations | **19/19 applied, 0 failed** on the staging database; 27 tables. |

## 6. Remaining blockers (all external to the codebase)

1. **Public HTTPS staging domain** — the production topology, secure
   cookies, exact CORS and the full auth lifecycle are now *proven
   locally over validated TLS*; what remains unproven is only internet-
   facing operation (ACME certificates, real domain DNS, CDN topology).
   Run the same stack on the staging host per `DEPLOYMENT.md`
   ("Staging deployment") — no code change required.
2. **Staging credentials** for Chapa / Google / OpenAI / SMTP (B1).
3. **GitHub repository variable** `NEXT_PUBLIC_API_BASE_URL` (B3) —
   exact steps in §2.

## 7. Final recommendation

### PRODUCTION BLOCKED — three external items remain

Everything executable without a public domain and without live
provider credentials has now been **executed, not assumed**: the exact
production deployment topology runs, serves validated HTTPS, enforces
the secure-cookie / exact-CORS / CSRF contract, passes the 22-check raw
HTTP audit and the full browser suite, migrates cleanly, and fails fast
on the last committed-secret gap (now closed). When the three external
items are resolved, re-run `staging-auth-audit.sh` against the public
URL, execute `verify-providers.mjs` with real credentials, and the
classification can become READY FOR PRODUCTION.
