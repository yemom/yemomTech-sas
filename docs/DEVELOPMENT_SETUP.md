# Development Setup

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 (tested: 21.0.9) | Backend targets Java 21 language level |
| Node.js | 22 LTS+ (tested: 22.15.1) | Next.js 16 requires Node ≥ 20.9 |
| npm | 11.x | `package-lock.json` is committed; CI uses `npm ci` |
| Docker | 28.x | Postgres 17 + Redis 7 for local dev; Testcontainers for ITs |
| Git | any recent | |

Maven is **not** installed separately — use the wrapper (`./mvnw` /
`.\mvnw.cmd`).

## Ports used in this checkout

| Port | Service | Why |
| --- | --- | --- |
| **5632** | PostgreSQL (host-mapped) | 5432 is occupied on this machine |
| **6637** | Redis (host-mapped) | 6379 is occupied (docker backend) |
| **8082** | Spring Boot (dev run) | 8080 held by a local httpd |
| **3001** | Next.js dev server | 3000 held by wslrelay/docker |

`.env` / `.env.example` already carry these values
(`DATABASE_URL=jdbc:postgresql://127.0.0.1:5632/bizgrow`,
`REDIS_PORT=6637`, `APP_PORT=8082` via run command,
`NEXT_PUBLIC_API_BASE_URL=http://localhost:8082/api/v1`,
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
$env:APP_PORT = '8082'
.\mvnw.cmd spring-boot:run
# first boot runs Flyway V1-V7 against the empty database

# 4. Frontend (new terminal)
cd frontend
npm ci                     # exact lockfile install
npm run dev                # http://localhost:3001
```

The `dev` profile is the default (`spring.profiles.default: dev`), which
enables dev CORS defaults and (optionally) `expose-dev-token`.

## Environment variables

Full template with comments: [`.env.example`](../.env.example). Summary:

| Variable | Purpose | Dev value |
| --- | --- | --- |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres JDBC + credentials | `jdbc:postgresql://127.0.0.1:5632/bizgrow` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis for login throttling | `127.0.0.1:6637`, empty password |
| `JWT_SECRET` | HS256 signing key (≥ 32 bytes) | dev default in `application-dev.yml`; **generate your own**: `[Convert]::ToBase64String((1..64 | ForEach-Object {Get-Random -Maximum 256}))` |
| `JWT_ACCESS_EXPIRATION_SECONDS` / `JWT_REFRESH_EXPIRATION_DAYS` | Token lifetimes | `900` / `30` |
| `CORS_ALLOWED_ORIGINS` | Exact allowed origins, comma-separated | `http://localhost:3001` |
| `FRONTEND_URL` | Base URL for links (e.g. invitation emails) | `http://localhost:3001` |
| `COOKIE_SAME_SITE` / `COOKIE_SECURE` | Refresh cookie flags | `Lax` / `false` |
| `NEXT_PUBLIC_API_BASE_URL` | API base baked into the browser bundle | `http://localhost:8082/api/v1` |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_WINDOW_MINUTES` | Throttle window | `10` / `15` |

Rules:

- Base `application.yml` has **no secret defaults** — missing values fail
  startup (by design).
- Never put secrets in `NEXT_PUBLIC_*` (they ship to the browser).
- `.env` is gitignored; only `.env.example` is committed.

## Day-to-day commands

```powershell
# ---- backend ----
cd backend
.\mvnw.cmd -B verify                    # full gate: 100 unit + 16 IT (Testcontainers)
.\mvnw.cmd test                         # unit tests only
.\mvnw.cmd spring-boot:run              # dev server (APP_PORT env to move it)

# ---- frontend ----
cd frontend
npm run typecheck                       # tsc --noEmit
npm run lint                            # eslint .
npm run test                            # vitest (jsdom)
npm run build                           # next build (prerenders all routes)
npm run e2e                             # Playwright (needs backend+dev server up)

# ---- infrastructure ----
docker compose up -d postgres redis     # start db/cache
docker compose down                     # stop all (volumes kept)
docker compose down -v                  # stop + WIPE local data
```

## Ports busy? (this machine's known occupants)

- `3000` — wslrelay (PID varies) + docker backend
- `8080` — local httpd
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
| Login always 429 | Throttle window in Redis: `docker compose exec redis redis-cli KEYS 'login-throttle:*'` then `DEL` the key |
| Playwright: browser missing | `cd frontend; npx playwright install chromium` |
| E2E: `next` redirect test fails | Ensure the backend is on 8082 — otherwise CORS/network errors surface as login failures |
| "Checking your session…" stuck | Backend down (refresh failed silently) or cookie blocked — check `/actuator/health` |
| Spring Boot started but requests 401 | Access token expired — the client should auto-refresh; check DevTools/network tab |

## Editor / tooling

- VS Code: workspace settings committed under `.vscode/` (extensions
  recommended only).
- Next.js dev server regenerates `AGENTS.md` agent rules — committing that
  file keeps the tree clean (see the block in `frontend/AGENTS.md`).
- Lint: flat config `frontend/eslint.config.mjs` (Next.js core-web-vitals +
  TypeScript). Next 16 has no `next lint` — use `eslint .` directly.
