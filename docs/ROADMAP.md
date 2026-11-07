# Roadmap

Honest status: what exists today is marked **Done** and was verified by
tests; everything else is **not built** — no placeholders, no fake data.

## Done — first vertical slice (verified)

| Area | State |
| --- | --- |
| Auth: register / login / refresh / logout / me | ✅ JWT + rotation + family revocation + CSRF + throttling |
| Multi-tenancy: orgs, memberships, invitations | ✅ 404/403 rules, last-owner, no self-edit/escalation |
| Websites: register / list / update / soft-deactivate | ✅ normalization, per-org uniqueness, **no fetching (no SSRF)** |
| Dashboard with real persisted counts | ✅ no invented metrics |
| Frontend: landing, auth, onboarding, workspace shell + 5 pages | ✅ typecheck/lint/unit/build green |
| Tests: 100 unit + 16 IT + 21 frontend unit + 4 E2E | ✅ all green |
| CI: backend + frontend workflows | ✅ written (not yet exercised on GitHub) |
| Docs: architecture, DB, API, security, tenancy, setup, deploy, testing, ADRs | ✅ this folder |

## Next — completing the product loop

1. **E-mail delivery for invitations** — real SMTP/SES provider behind
   `InvitationDelivery`; today production logs the issuance without the
   token (dev returns it in-response behind `expose-dev-token`).
2. **E-mail verification & password reset** — `users.email_verified` and
   `PENDING_VERIFICATION` already exist in the schema; the flows don't.
3. **Organization restore (un-archive)** — archive is currently one-way;
   needs an owner-only `POST /organizations/{id}/restore` + tests. The
   schema already supports it (`status` is a CHECK enum).
4. **Session management UI** — "sign out other devices" (revoke all
   refresh families for a user) — data model supports it, no endpoint yet.
5. **Audit log browsing** — `audit_logs` table + `AuditService` are live;
   an owner-visible activity page would surface them.

## After that — the modules the permission matrix already reserves

Permissions `SEO_MANAGE`, `LEAD_MANAGE`, `ANALYTICS_VIEW` are defined (with
roles) but **the modules do not exist**. Build order:

1. **SEO auditing** — per-website crawl & issue tracking. *Hard
   prerequisite:* SSRF-safe fetching (re-run `WebsiteUrlNormalizer` +
   DNS resolution + private-IP blocking on every fetch, allowlist ports,
   size/time limits, per-org crawl budget) — deliberately deferred until
   then.
2. **Leads** — CRM-lite for sales roles (`SALES_MANAGER`/`SALES_AGENT`
   grants already defined).
3. **Analytics** — real metrics only, computed from real events; the
   dashboard must never show placeholder numbers.
4. **AI features** — actual model integration behind a metered service;
   nothing "AI-flavored" ships without a real model behind it.

## Operational backlog

- GitHub Actions: run the workflows for real (they are written but this
  repo has no remote yet); add a Docker image build/publish job.
- Staging environment (compose or Render blue/green) + smoke script in CI.
- Observability: structured JSON logs with `traceId`, error tracking
  (Sentry/OpenTelemetry), dashboards for login-throttle and 5xx rates.
- Backup/restore drill for Postgres; document RPO/RTO.
- Rate limiting beyond login (per-IP API budgets) once behind a proxy/CDN.
- Row-level security as defense-in-depth if a second data-access path
  appears (see MULTI_TENANCY §9).

## Explicit non-goals (for now)

- Mobile apps (API is ready for them — bearer + refresh cookie design
  works for a future native client).
- Multi-region deployment, SSO/SAML, SOC 2 evidence collection.
- Payments/billing — no payment provider is integrated and none will be
  faked.
