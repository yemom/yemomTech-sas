# Multi-Tenancy

YemomTech uses **shared-table multi-tenancy**: one database, one schema, and
every tenant-owned row carries `organization_id`. This document defines the
tenant model, the authorization rules, and how isolation is enforced and
tested.

## 1. The tenant = organization

- `organizations` is the tenant root; children are `organization_members`,
  `organization_invitations`, `websites`, `audit_logs` — all with
  `organization_id NOT NULL` (audit: nullable for user-level events) and
  `ON DELETE CASCADE` from `organizations`.
- Organization lifecycle: `ACTIVE → ARCHIVED` (soft delete via
  `DELETE /organizations/{id}`, owner-only). `ARCHIVED` hides the org from
  `GET /auth/me` and makes it behave as non-existent (see §4).
- A user may belong to many organizations; the frontend persists the last
  selected one (`localStorage.bizgrow.current_org`) but re-validates it
  against live memberships on every bootstrap.

## 2. Membership is the ONLY authorization source

> The JWT proves *who you are*. The `organization_members` row proves *what
> you may do here*. Nothing else counts.

- On every tenant request the access-control layer re-reads the membership
  row (`AccessControlService`) — no roles in the JWT, no cached grants, no
  header-based tenancy (`X-Org-Id` is never trusted).
- The effective check order:

```
1. Is the caller authenticated?            no → 401
2. Does the membership row exist & ACTIVE? no → 404   (org missing OR caller not a member)
3. Is the org ACTIVE?                      no → 404   (ARCHIVED behaves as gone)
4. Does the role have the permission?      no → 403   (member, but not allowed)
5. Business rules (self-edit, escalation…) no → 403/409
```

**404 vs 403 rule:** non-membership and ARCHIVED orgs return **404** — a
caller must not learn that an org exists; a member who lacks a permission
gets **403** because the existence of the resource is already known to them.

## 3. Permission matrix

Single source of truth: `com.bizgrow.membership.Permission` (an `EnumMap` of
permission → allowed roles). Services ask
`permission.allowedFor(role)` and never re-implement role checks inline. The
frontend mirrors it in `src/lib/api/permissions.ts` for UI affordances only —
**the backend is authoritative**.

| Capability | OWNER | ADMIN | MKT | SALES-MGR | SALES-AGENT | ANALYST | VIEWER |
| --- | --- | --- | --- | --- | --- | --- | --- |
| View organization / members / websites | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Update organization | ✓ | ✓ | – | – | – | – | – |
| Manage members (role/suspend/remove) | ✓ | ✓ | – | – | – | – | – |
| Create/revoke invitations | ✓ | ✓ | – | – | – | – | – |
| Register/manage websites | ✓ | ✓ | ✓ | – | – | – | – |
| Manage SEO (future) | ✓ | ✓ | ✓ | – | – | – | – |
| Manage leads (future) | ✓ | ✓ | – | ✓ | ✓ | – | – |
| View analytics (future) | ✓ | ✓ | ✓ | ✓ | limited | ✓ | read-only |
| **Archive organization** | ✓ | – | – | – | – | – | – |

`Permission` also defines the future grants (`SEO_MANAGE`, `LEAD_MANAGE`,
`ANALYTICS_VIEW`) so the matrix is stable as modules land.

## 4. Archiving (soft delete) semantics

- Owner-only (`ORG_DELETE`); idempotent (repeat DELETE → 204).
- Effect: status `ARCHIVED`.
  - `GET /auth/me` **filters ARCHIVED memberships out** of the briefs — the
    workspace disappears from the switcher.
  - `AccessControlService` 404s any request to an archived org (membership
    is checked first, then status) — every member loses access uniformly.
- Rows are never deleted: audit trail preserved, recovery possible later
  (restore flow is on the ROADMAP).

## 5. Membership management rules

Enforced in `MembershipService` (covered by `TenantIsolationIT`):

| Rule | Result |
| --- | --- |
| Actor must have `MEMBER_MANAGE` | 403 otherwise |
| **No self-edit**: changing your own role or status | 403 |
| **No self-remove**: DELETE on own membership | 403 |
| **No escalation**: setting a role above the actor's own | 403 |
| **Last-owner protection**: demoting/removing the final ACTIVE owner | 409 |
| Target membership belongs to the URL's organization | 404 (cross-tenant id) |
| `REMOVED` via PATCH | 400 — DELETE is the removal endpoint |

Invitations follow the same escalation logic: you cannot invite above your
own rank, and `BUSINESS_OWNER` is never invitable (400).

## 6. Tenant scoping in queries

- Every repository query for tenant data takes `organizationId` as an
  explicit parameter (e.g. `findByOrganizationIdAndStatus(...)`,
  `existsByOrganizationIdAndDomain(...)`).
- Controllers derive the org id from the **URL path**, but authorization is
  the membership re-read — a forged path with a valid token of a non-member
  still 404s.
- Cross-org reads in one statement are forbidden; `OrganizationReadService`
  batches by id list for dashboard assembly.
- Website uniqueness is `UNIQUE (organization_id, domain)` — scoped by
  construction at the DB level.
- Dashboard/member/invitation/website counts are always computed within the
  authorized org.

## 7. Frontend tenancy

- `RequireAuth` gates all `/workspace/*` routes; unauthenticated →
  `/login?next=<path>` (same-app paths only).
- The workspace layout redirects authenticated users with **zero**
  memberships to `/onboarding` (queries stay disabled otherwise — a real bug
  found during UI testing).
- Query keys are namespaced per org
  (`["organizations", orgId, "websites", page]`) so switching workspaces
  never flashes another tenant's data.
- The UI hides affordances the role can't use (e.g. non-admins don't see the
  invite form; the owner row's Suspend/Remove buttons are disabled) — purely
  cosmetic; the API enforces the real rules.

## 8. How isolation is verified

- **`TenantIsolationIT`** (Testcontainers): two users, two orgs — asserts
  cross-org PATCH → 404, member endpoint cross-org → 404, escalation → 403,
  last-owner → 409.
- **`VerticalSliceIT`**: full happy path plus negative cases (invalid URL →
  400 envelope, duplicate domain → 409, CSRF → 403, refresh reuse → family
  revocation).
- **Frontend unit tests**: permission matrix mirrored test
  (`permissions.test.ts`) fails if the frontend matrix drifts from the
  documented backend matrix.
- **E2E**: `frontend/e2e/vertical-slice.spec.ts` walks the real UI against
  the real backend, including the self-suspend/Remove-disabled assertions.

## 9. Future concerns (not yet implemented)

- **Per-org data export/deletion** (GDPR-style) — schema supports it
  (cascade + audit SET NULL), no endpoint yet.
- **Row-level security (Postgres RLS)** as defense-in-depth — deliberately
  not added yet: the application layer is tested, and RLS would require
  setting `organization_id` on every connection; revisit if a second data
  access path (e.g. reporting replica) appears.
- **Org restore** after archive (see ROADMAP).
