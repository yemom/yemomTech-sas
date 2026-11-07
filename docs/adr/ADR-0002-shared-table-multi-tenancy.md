# ADR-0002: Shared-table multi-tenancy with `organization_id`

- **Status:** Accepted (2026-11)
- **Context:** Options for tenant isolation: (a) database-per-tenant,
  (b) schema-per-tenant, (c) shared tables with a tenant column.
  Tenants are expected to be numerous and small (Ethiopian SMEs); the team
  is small and operations must stay simple.
- **Decision:** **(c) Shared tables** — every tenant-owned row carries
  `organization_id` with `ON DELETE CASCADE` from `organizations`.
  Authorization is centralized in `AccessControlService`, which re-reads
  the `organization_members` row on every request (the JWT carries **no**
  org roles). Non-membership and ARCHIVED orgs → **404**; missing
  permission → **403**.
- **Consequences:**
  - ✅ One database to migrate, back up, and reason about; queries like
    dashboard assembly span an org's data in one connection.
  - ✅ Per-org uniqueness is expressible as a composite constraint
    (`UNIQUE (organization_id, domain)`).
  - ❌ A missing `organization_id` predicate in a query is a cross-tenant
    leak — mitigated by repository signatures that always take the org id,
    plus `TenantIsolationIT` covering the attack paths.
  - ❌ Noisy-neighbour risk (one org's load affects others) — acceptable
    at this scale.
  - Deferred: Postgres row-level security as a second line of defense
    (documented in MULTI_TENANCY.md §9) if a second data-access path
    appears.
