# YemomTech (YemomBizGrow)

AI-powered growth platform for Ethiopian businesses — websites, team
collaboration, and (future) SEO/lead/analytics modules in one secure
multi-tenant workspace.

**Status:** one complete vertical slice is implemented and verified:
authentication, multi-tenancy, organizations, memberships, invitations, and
website registration — with unit tests, Testcontainers integration tests,
browser E2E tests, CI workflows, and full documentation.

## Repository layout

```
backend/    Spring Boot 3.5.16 / Java 21 modular monolith (com.YemomBizGrow)
frontend/   Next.js 16 (App Router) / React 19 / TypeScript strict
docs/       Architecture, security, database, API, testing, roadmap docs + ADRs
infrastructure/docker/  Backend & frontend Dockerfiles
.github/workflows/      Backend CI and Frontend CI
docker-compose.yml      Development-only Postgres 17 + Redis + backend + frontend
```

## Quick start (development)

Prerequisites: **JDK 21**, **Node 22+**, **Docker** (for Postgres/Redis).

```powershell
# 1. Infrastructure (Postgres on 5632, Redis on 6637)
docker compose up -d postgres redis

# 2. Environment
copy .env.example .env        # PowerShell; values already target local dev

# 3. Backend (defaults to the `dev` profile, port 8082 locally in this checkout)
cd backend
$env:APP_PORT='8082'; .\mvnw.cmd spring-boot:run

# 4. Frontend (port 3001 — 3000 is occupied on this machine)
cd frontend
npm ci
npm run dev
```

Open **http://localhost:3001** — register, create a workspace, register a
website, invite teammates.

- Backend API docs (dev): http://localhost:8082/swagger-ui.html
- Health: http://localhost:8082/actuator/health

## Quality gates

```powershell
# Backend: 100 unit tests + 16 integration tests (Testcontainers)
cd backend; .\mvnw.cmd -B verify

# Frontend: typecheck, lint, unit tests, production build
cd frontend
npm run typecheck
npm run lint
npm run test
npm run build

# Browser E2E (backend on 8082 + dev server on 3001 must be running)
npm run e2e
```

Latest verified results: backend `BUILD SUCCESS` (116 tests, 0 failures),
frontend all green (21 unit tests, 11 routes built), E2E suite in
`frontend/e2e/vertical-slice.spec.ts`.

## Key design decisions (details in `docs/`)

- **Modular monolith** — one deployable, modules (`auth`, `user`,
  `organization`, `membership`, `invitation`, `website`, `audit`) with no
  cross-module entity access outside each module's service layer.
- **Shared-table multi-tenancy** — every tenant row carries `organization_id`;
  **membership is the only authorization source**; a non-member gets `404`,
  a member without the permission gets `403`.
- **Auth** — short-lived JWT access token (memory only, never persisted),
  refresh token rotation with reuse detection → family revocation, HttpOnly
  refresh cookie + double-submit CSRF, Redis login throttling (fail-open).
- **No fake capabilities** — no mocked payments, AI, or metrics. Website
  registration stores metadata only; the backend **never fetches URLs**
  (deliberately no SSRF surface).
- **Flyway owns the schema** (`ddl-auto: validate`); Ethiopian defaults
  (`ET` / `ETB` / `Africa/Addis_Ababa`) are column-level defaults.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/SYSTEM_ARCHITECTURE.md](docs/SYSTEM_ARCHITECTURE.md) | Modules, layers, request flow, error envelope |
| [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) | Schema, migrations V1–V7, invariants |
| [docs/API_SPECIFICATION.md](docs/API_SPECIFICATION.md) | Every endpoint, payloads, pagination, status codes |
| [docs/AUTHENTICATION_AND_SECURITY.md](docs/AUTHENTICATION_AND_SECURITY.md) | JWT, refresh rotation, CSRF, throttling, password policy |
| [docs/MULTI_TENANCY.md](docs/MULTI_TENANCY.md) | Tenant model, permission matrix, isolation rules |
| [docs/DEVELOPMENT_SETUP.md](docs/DEVELOPMENT_SETUP.md) | Prerequisites, ports, environment variables |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production topology, environment, runbook |
| [docs/TESTING.md](docs/TESTING.md) | Unit, integration, E2E strategy and how to run |
| [docs/ROADMAP.md](docs/ROADMAP.md) | What exists, what is next |
| [docs/adr/](docs/adr/) | Architecture decision records |

## License

See [LICENSE](LICENSE).
