# API Specification

Base path: **`/api/v1/`**. Content type `application/json; charset=UTF-8`.
Interactive docs (dev): `/swagger-ui.html`, OpenAPI at `/v3/api-docs`.

Conventions:

- **Collections are always paginated** — `?page=` (0-based, default `0`) and
  `?size=` (default `20`, max `100`; out-of-range → 400).
- **Errors always use the uniform envelope**
  (see [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)).
- DTOs only; entities are never serialized. Timestamps are RFC 3339 UTC
  instants.
- Auth schemes:
  - `Bearer` — `Authorization: Bearer <accessToken>` (in-memory on the
    frontend, 15 min default).
  - `Cookie + CSRF` — refresh/logout only: HttpOnly refresh cookie plus
    `X-CSRF-TOKEN` header echoing the non-HttpOnly `csrf` cookie
    (double-submit). Missing/wrong CSRF → 403.

## Auth (`/auth`)

| Method | Path | Auth | Success | Notes |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | public | **201** `AuthSessionResponse` | Sets refresh cookie; duplicates → 409 |
| POST | `/auth/login` | public | **200** `AuthSessionResponse` | Generic error on bad credentials (no enumeration); throttling → 429 |
| POST | `/auth/refresh` | cookie+CSRF | **200** `AuthSessionResponse` | Rotates refresh token; missing cookie → 403; reuse → family revoked + 401 |
| POST | `/auth/logout` | cookie+CSRF | **204** | Revokes the family, clears cookies; idempotent |
| GET | `/auth/me` | Bearer | **200** `MeResponse` | Bootstrap payload: user + membership briefs (ARCHIVED orgs filtered out) |

### `AuthSessionResponse`

```json
{
  "accessToken": "eyJ…",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "csrfToken": "…",
  "user": { "id": "…", "firstName": "…", "lastName": "…", "email": "…" }
}
```

The refresh token is **never** in the body — only in the `HttpOnly` cookie.

### `RegisterRequest`

```json
{
  "firstName": "Hiwot",
  "lastName": "Tesfaye",
  "email": "hiwot@example.et",
  "password": "Correct-Horse-9!",
  "phoneNumber": "+251911234567"
}
```

`phoneNumber` optional; all other fields required. Password policy:
8–128 chars, upper + lower + digit + symbol (see
[AUTHENTICATION_AND_SECURITY.md](AUTHENTICATION_AND_SECURITY.md)).

### `MeResponse`

```json
{
  "user": { "id": "…", "firstName": "…", "lastName": "…", "email": "…", "status": "ACTIVE" },
  "memberships": [
    { "organizationId": "…", "name": "Hiwot Trading PLC", "slug": "hiwot-trading-plc",
      "status": "ACTIVE", "role": "BUSINESS_OWNER" }
  ]
}
```

Roles are read live from the DB (never from the JWT) — the UI displays them,
the backend independently re-checks every call.

## Organizations (`/organizations`)

| Method | Path | Permission | Success | Notes |
| --- | --- | --- | --- | --- |
| POST | `/organizations` | authenticated | **201** `OrganizationResponse` | Creator becomes `BUSINESS_OWNER`; defaults ET/ETB/Addis; duplicate slug → 409 |
| GET | `/organizations` | authenticated | **200** list of `OrganizationResponse` | Only orgs the caller is an ACTIVE member of |
| GET | `/organizations/{id}` | `ORG_VIEW` | **200** | Non-member → **404** |
| GET | `/organizations/{id}/dashboard` | `ORG_VIEW` | **200** `DashboardResponse` | Real persisted counts only |
| PATCH | `/organizations/{id}` | `ORG_UPDATE` | **200** | True PATCH: only non-null fields applied; **empty string clears** optional text fields; `slug`/`status`/locale not accepted (no mass assignment) |
| DELETE | `/organizations/{id}` | `ORG_DELETE` (owner) | **204** | Soft-archive (`status=ARCHIVED`), idempotent |

### `CreateOrganizationRequest`

```json
{ "name": "Hiwot Trading PLC", "industry": "Retail",
  "description": "Optional pitch", "websiteUrl": "https://example.et" }
```

Only `name` is required. `industry` is uppercased by the service.
`websiteUrl` optional; empty/blank → cleared (null).

### `OrganizationResponse` (excerpt)

```json
{
  "id": "…", "name": "Hiwot Trading PLC", "slug": "hiwot-trading-plc",
  "description": null, "industry": "RETAIL", "websiteUrl": null,
  "countryCode": "ET", "currencyCode": "ETB", "timezone": "Africa/Addis_Ababa",
  "status": "ACTIVE", "createdBy": "…",
  "memberCount": 1, "websiteCount": 0,
  "createdAt": "…", "updatedAt": "…"
}
```

### `DashboardResponse`

```json
{
  "organization": { "…": "OrganizationResponse" },
  "recentWebsites": [ { "…": "WebsiteResponse" } ],
  "onboarding": { "websiteRegistered": false, "hasAdditionalMember": false }
}
```

No traffic/leads/revenue/AI fields exist — those modules are not built and
nothing is fabricated.

## Members (`/organizations/{organizationId}/members`)

| Method | Path | Permission | Success |
| --- | --- | --- | --- |
| GET | `…/members?page&size` | `MEMBER_VIEW` | **200** `PageResponse<MemberResponse>` |
| PATCH | `…/members/{memberId}` | `MEMBER_MANAGE` | **200** `MemberResponse` |
| DELETE | `…/members/{memberId}` | `MEMBER_MANAGE` | **200** `MemberResponse` (soft `REMOVED`) |

Rules enforced in the service (see MULTI_TENANCY.md):

- No self-role-edit and no self-suspend/self-remove (403).
- No privilege escalation above one's own role (403).
- Last owner cannot be demoted or removed (409).
- `REMOVED` status via PATCH is rejected — DELETE is the removal endpoint.

## Invitations (`/organizations/{organizationId}/invitations`)

| Method | Path | Permission | Success |
| --- | --- | --- | --- |
| POST | `…/invitations` | `INVITE_MANAGE` | **201** `InvitationResponse` |
| GET | `…/invitations?page&size` | `INVITE_MANAGE` | **200** `PageResponse<InvitationResponse>` |
| POST | `…/invitations/{invitationId}/revoke` | `INVITE_MANAGE` | **200** `InvitationResponse` |
| POST | `/invitations/accept` | Bearer | **200** `InvitationAcceptResponse` |

- 7-day expiry; token stored as SHA-256 only; single-use (accepted *or*
  revoked).
- `BUSINESS_OWNER` cannot be granted by invitation (400) — owner transfer is
  not an invitation flow.
- Duplicate pending invitation or existing member → 409.
- Accept bound to the invited e-mail: wrong account → 403; unknown/expired/
  used token → 401.
- `InvitationResponse.devAcceptToken` is non-null **only** when the dev
  profile enables `app.security.invitation.expose-dev-token` (production
  startup validation forbids it).

## Websites (`/organizations/{organizationId}/websites`)

| Method | Path | Permission | Success |
| --- | --- | --- | --- |
| POST | `…/websites` | `WEBSITE_MANAGE` | **201** `WebsiteResponse` |
| GET | `…/websites?page&size` | `WEBSITE_VIEW` | **200** `PageResponse<WebsiteResponse>` |
| GET | `…/websites/{websiteId}` | `WEBSITE_VIEW` | **200** |
| PATCH | `…/websites/{websiteId}` | `WEBSITE_MANAGE` | **200** (re-normalized URL, uniqueness re-checked) |
| DELETE | `…/websites/{websiteId}` | `WEBSITE_MANAGE` | **200** soft `INACTIVE`, idempotent |

### URL rules (no SSRF surface)

`WebsiteUrlNormalizer` enforces, server-side:

1. `http://` or `https://` only (scheme-insensitive input, output lowercased);
2. **public domains only** — rejects `localhost`, loopback/private/link-local
   IP literals, embedded credentials, non-HTTP schemes;
3. host lowercased, path/query preserved, trailing dot removed;
4. `domain` derived (host only) and unique **per organization**
   (same domain in another tenant is fine; duplicate in the same tenant → 409);
5. **the URL is never fetched** — registration is metadata-only by design.

### `CreateWebsiteRequest`

```json
{ "name": "Company site", "url": "HTTPS://WWW.Example.COM/" }
```

Stored normalized: `domain = "www.example.com"`,
`url = "https://www.example.com/"`.

## Health & ops

| Path | Auth | Notes |
| --- | --- | --- |
| `/actuator/health` | public | Liveness/readiness probes enabled; details hidden |
| `/actuator/health/readiness`, `/actuator/health/liveness` | public | For orchestrators |
| `/swagger-ui.html`, `/v3/api-docs` | public in dev | Disable in production (`springdoc.*.enabled=false`) |

Only `health` and `info` are exposed (`management.endpoints.web.exposure`).

## Status-code cheat sheet

| Code | When |
| --- | --- |
| 400 | Bean validation, bad pagination, invalid URL, weak password, owner-by-invitation |
| 401 | Missing/invalid bearer token; bad/expired/reused refresh; wrong accept token |
| 403 | Missing permission on an org you belong to; CSRF failure; self-edit/escalation; wrong invitation e-mail |
| 404 | Resource not visible: non-member, ARCHIVED org, unknown id — **no existence leak** |
| 409 | Duplicate e-mail/slug/domain/invitation; last-owner violation |
| 429 | Login throttling window exceeded |
| 500 | Unexpected — envelope carries `traceId` only |
