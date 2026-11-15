# Development Setup

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 (tested: 21.0.9) | Backend targets Java 21 language level |
| Node.js | **22 LTS** (tested: 22.15.1) | One version across local dev, CI and the Docker images. Next.js 16 requires Node ≥ 20.9. |
| npm | 11.x | `package-lock.json` is committed; CI uses `npm ci` |
| Docker | 28.x | Postgres 17 + Redis 7 for local dev; Testcontainers for ITs |
| Git | any recent | |

Maven is **not** installed separately — use the wrapper (`./mvnw` /
`.\mvnw.cmd`).

## Ports used in this checkout

One canonical port per service. Every configuration file, the Docker Compose
stack, the Playwright config and the documentation use exactly these values —
`frontend/src/lib/api/api-base-url.test.ts` fails the build if they drift.

| Port | Service | Why |
| --- | --- | --- |
| **5635** | PostgreSQL (host-mapped) | 5432 is occupied on this machine |
| **6637** | Redis (host-mapped) | 6379 is occupied (docker backend) |
| **8080** | Spring Boot | the backend API base for the browser |
| **3001** | Next.js (dev server *and* the Docker frontend) | 3000 held by wslrelay/docker |
| **3101** | Next.js (E2E server only) | a dedicated port so the test suite never collides with the dev server or the Docker stack |

The canonical API base URL is **`http://localhost:8080/api/v1`**. It appears
in `.env`, `.env.example`, `frontend/.env.example`, `frontend/next.config.ts`
(the fallback), `frontend/src/lib/api/client.ts` (the fallback),
`docker-compose.yml` and `docs/TESTING.md` — always as the same value.

> The frontend previously fell back to port **8082** in `next.config.ts` while
> everything else used 8080. The symptom was a frontend that started cleanly
> and then failed every API call with a connection error. The stale value is
> gone and a unit test now guards it.

`.env` / `.env.example` already carry these values
(`DATABASE_URL=jdbc:postgresql://localhost:5635/YemomBizGrow`,
`REDIS_PORT=6637`, `APP_PORT=8080`,
`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1`,
`CORS_ALLOWED_ORIGINS=http://localhost:3001`). If your machine is free to
use the default ports (5432/6379/8080/3000), change them consistently in
`.env` **and** the run commands.

## First-time setup

```powershell
# 1. Environment file (never commit .env)
Copy-Item .env.example .env

# 2. Infrastructure
docker compose up -d postgres redis
docker compose ps          # both should be "healthy"

# 3. Backend
cd backend
.\mvnw.cmd spring-boot:run
# first boot runs Flyway V1-V7 against the empty database

# 4. Frontend (new terminal)
cd frontend
npm ci                     # exact lockfile install
npm run dev                # http://localhost:3001
```

The `dev` profile is the default (`spring.profiles.default: dev`), which
enables dev CORS defaults and (optionally) `expose-dev-token`.

### Or run the whole stack in Docker

```powershell
docker compose up -d --build
docker compose ps
```

All four services publish their ports (backend 8080, PostgreSQL 5635, Redis
6637, frontend 3001) and report **healthy**.

## Environment variables

Full template with comments: [`.env.example`](../.env.example). Summary:

| Variable | Purpose | Dev value |
| --- | --- | --- |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres JDBC + credentials | `jdbc:postgresql://localhost:5635/YemomBizGrow` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis for login throttling | `localhost`, `6637`, empty password |
| `JWT_SECRET` | HS256 signing key (≥ 32 bytes) | dev default in `application-dev.yml`; **generate your own**: `[Convert]::ToBase64String((1..64 | ForEach-Object {Get-Random -Maximum 256}))` |
| `JWT_ACCESS_EXPIRATION_SECONDS` / `JWT_REFRESH_EXPIRATION_DAYS` | Token lifetimes | `900` / `30` |
| `CORS_ALLOWED_ORIGINS` | Exact allowed origins, comma-separated | `http://localhost:3001` |
| `FRONTEND_URL` | Base URL for links (e.g. invitation emails) | `http://localhost:3001` |
| `COOKIE_SAME_SITE` / `COOKIE_SECURE` | Refresh cookie flags | `Lax` / `false` |
| `NEXT_PUBLIC_API_BASE_URL` | API base baked into the browser bundle | `http://localhost:8080/api/v1` |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_WINDOW_MINUTES` | Throttle window | `10` / `15` |

Rules:

- Base `application.yml` has **no secret defaults** — missing values fail
  startup (by design).
- Never put secrets in `NEXT_PUBLIC_*` (they ship to the browser).
- `.env` is gitignored; only `.env.example` is committed.

### Spring profiles

| Profile | Behaviour |
| --- | --- |
| `dev` | Local defaults, `expose-dev-token=true`, mail health indicator off (no SMTP locally). |
| `test` | Used by the integration tests; Testcontainers provide the infrastructure. |
| `prod` | Strict. `application-prod.yml` disables the API documentation, forces `SameSite=None` + `Secure` cookies and refuses `expose-dev-token`. |

`prod` and `dev`/`test`/`local` **must not be combined**: a mixed profile set
is rejected at startup by `SecurityProfileMode`, because a production
deployment that also activates `dev` would silently inherit development-grade
secrets. Startup validation is strict whenever a production profile is
active.

## Day-to-day commands

```powershell
# ---- backend ----
cd backend
.\mvnw.cmd -B clean verify               # full gate: 301 unit + 30 IT (Testcontainers)
.\mvnw.cmd test                          # unit tests only
.\mvnw.cmd spring-boot:run               # dev server (APP_PORT env to move it)

# ---- frontend ----
cd frontend
npm run typecheck                        # tsc --noEmit
npm run lint                             # eslint .
npm run test                             # vitest (jsdom)
npm run build                            # next build (prerenders all routes)
npm run e2e                              # Playwright (starts its own production server)
npm run e2e:server                       # build + serve the standalone server on 3001

# ---- infrastructure ----
docker compose up -d postgres redis     # start db/cache
docker compose down                     # stop all (volumes kept)
docker compose down -v                  # stop + WIPE local data

# ---- database backup / restore ----
bash infrastructure/scripts/postgres-backup.sh backup    # dump to ./backups
bash infrastructure/scripts/postgres-backup.sh verify    # backup + restore drill
```

The `verify` drill restores the dump into an isolated scratch database,
compares every table, checks the Flyway history and drops the scratch
database again — it never modifies the application database. See
[DEPLOYMENT.md](DEPLOYMENT.md#database-backup-and-restore).

### Running the E2E suite

`npm run e2e` builds the frontend, starts the **production** server on 3101
and runs Playwright against it. It needs the **backend running on 8080**;
Playwright starts and stops the frontend itself.

```powershell
cd backend; .\mvnw.cmd spring-boot:run     # terminal 1
cd frontend; npm run e2e                   # terminal 2
```

Two things to know:

- The E2E server has its **own port (3101)** and `reuseExistingServer` is
  `false`. The dev server (3001) and the Docker frontend (3001) can stay
  running — the suite never reuses either of them, because reusing "whatever
  happens to be listening" is exactly how a test run ends up proving nothing.
- The suite runs against a production build on purpose — see
  [TESTING.md](TESTING.md#the-server-under-test) for why `next dev` makes the
  suite flaky.

In CI the flow is inverted: the workflow starts the whole Docker stack and
points the suite at the container that was just built
(`E2E_BASE_URL=http://localhost:3001`).

## Ports busy? (this machine's known occupants)

- `3000` — wslrelay (PID varies) + docker backend
- `5432` / `6379` — other Docker containers

If `npm run dev` or `spring-boot:run` fails with "address already in use",
check with `Get-NetTCPConnection -LocalPort <port> -State Listen`.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `npm install` fails with `ECONNRESET` | Flaky network — retry: `npm install --prefer-offline --fetch-retries=10 --maxsockets=3` |
| Backend won't start: `Database info … password` | `docker compose up -d postgres`, check `.env` matches compose |
| `ddl-auto: validate` fails after pull | A migration changed — never edit applied migrations; add `Vn+1__…sql` |
| CORS error in browser | Origin must be listed **exactly** in `CORS_ALLOWED_ORIGINS` (no trailing slash, no wildcard) |
| Login always 429 | Throttle window in Redis: `docker compose exec redis redis-cli KEYS 'auth:login:*'` then `DEL` the key. The key is the SHA-256 of the e-mail, not the address itself. |
| Playwright: browser missing | `cd frontend; npx playwright install chromium` (and install Google Chrome for the `chrome` project) |
| E2E: "port 3001 is already used" | The E2E server owns port 3101 and never reuses a running server. If 3101 is taken, stop that process — and check that you are not running the suite twice. |
| E2E: registration stays on `/register` | Check the backend is on **8080** and CORS allows the E2E origin (`http://localhost:3101`); also confirm the frontend is served by the production build, not a stale `next dev` |
| "Checking your session…" stuck | Backend down (refresh failed silently) or cookie blocked — check `/actuator/health/readiness` |
| Spring Boot started but requests 401 | Access token expired — the client should auto-refresh; check DevTools/network tab |
| `/actuator/health` reports DOWN locally | The aggregate endpoint includes optional components. The mail indicator is disabled in `dev` because there is no SMTP server locally; use `/actuator/health/readiness` (what the Docker healthcheck calls) to judge the service. |

## Editor / tooling

- VS Code: workspace settings committed under `.vscode/` (extensions
  recommended only).
- Next.js dev server regenerates `AGENTS.md` agent rules — committing that
  file keeps the tree clean (see the block in `frontend/AGENTS.md`).
- Lint: flat config `frontend/eslint.config.mjs` (Next.js core-web-vitals +
  TypeScript). Next 16 has no `next lint` — use `eslint .` directly.
