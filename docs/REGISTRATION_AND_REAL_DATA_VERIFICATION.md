# Registration and real-data verification

**Status: PARTIALLY VERIFIED** — source checks, backend unit tests, frontend checks, and the production frontend build passed. The real PostgreSQL integration suite, Docker build, and browser E2E could not run because the Docker engine was unavailable in this environment.

**Repository commit inspected:** `a8d9da2a46ac9bbbfc25dee49d2f615d5d55a99b`

## Root causes found

- The active root `.env` used `APP_PORT=8082` while Compose publishes the backend port from that value and the backend healthcheck probes container port 8080. This made the process/listener and healthcheck disagree. The frontend `.env.local` also pointed to 8082.
- The API client fallback still pointed to port 8082, independently of the corrected frontend example.
- Spring dev defaults used Docker-only hostnames/ports, so starting Spring directly on the host could not reach the Compose-published PostgreSQL and Redis ports.
- The Compose frontend was published at 3000 even though the local Next.js dev server and CORS configuration use 3001.
- Refresh rotation looked up a token and rotated it in separate transactions without locking the row. Two concurrent requests could both observe the same active token and create competing successors.
- The dashboard response omitted the current caller's role even though the dashboard UI showed a role. The UI instead read a potentially stale `/auth/me` value.
- Onboarding collected a description but omitted it from the organization-create request. Selecting a newly created organization could also consult an old React state closure immediately after refreshing `/auth/me`.
- The Redis example supplied a password even though the local Compose Redis service does not enable password authentication.

## Changes made

- Aligned the active local backend and browser API URLs with port 8080; kept PostgreSQL at host port 5635 and Redis at host port 6637 for host-run development. The Compose backend continues to connect to the service names and container ports.
- Updated the root environment template and Spring dev defaults to the published host ports, made the Redis example password empty, and published the Compose frontend on host port 3001. The active credential values were preserved and were not printed or added to Git.
- Changed the API client's fallback to `http://localhost:8080/api/v1` and updated the existing Playwright flow's backend documentation.
- Added a pessimistic row lock for refresh-token lookup and placed the complete refresh operation in a transaction. The existing token rotation, family-reuse response, HttpOnly cookie, and CSRF rules remain in place. Updated unit-test stubs to exercise the locked lookup.
- Forwarded the optional onboarding description, kept the authenticated `/auth/me` membership snapshot in a ref for immediate organization selection, and removed a debug console message.
- Added `currentUserRole` to the dashboard DTO, derived it from the active membership required by the authorization check, and used the returned role in the dashboard UI. Expanded the existing real-database integration test to assert normalized email, a stored BCrypt hash, owner membership, persisted counts, recent website, and onboarding progress. Extended the existing Playwright scenario to reload and confirm the workspace session.

## Flow and database evidence

Source inspection confirms the existing registration controller calls `AuthService`, which delegates persistence and normalization to `UserService`. `UserService` stores a BCrypt hash and relies on the unique database email constraint in addition to its duplicate check. Registration then creates a refresh-token family, returns an access-token session, and sets the refresh and CSRF cookies. The frontend stores the access token in memory and the CSRF value for refresh; it does not persist the refresh token in browser storage.

On a browser reload, the frontend calls `/auth/refresh`, adopts the rotated session, and fetches `/auth/me` before protected routes render. Organization creation is transactional and creates the active owner membership using the authenticated principal. The dashboard checks active membership on each request and reads organization, membership counts, website counts, recent websites, and onboarding state from persisted records.

The integration test code now checks these database-backed outcomes, but it could not execute in this environment. **No registration, login, refresh, organization insert, or dashboard query was performed against PostgreSQL during this run.** No database verification evidence is claimed.

## Verification results

- Frontend `npm ci --no-audit --no-fund`: passed; installed 460 locked packages.
- Frontend `npm run lint`: passed.
- Frontend `npm run typecheck`: passed.
- Frontend `npm run test`: passed — 2 test files, 21 tests.
- Frontend `npm run build`: passed — Next.js production build completed.
- Backend `mvn clean test`: passed — 233 tests, 0 failures, 0 errors, 0 skipped. This compiled 238 production source files and 28 test source files.
- Backend `mvn clean test-compile -DskipTests`: passed after the final database integration assertions were added.
- Backend `mvn clean verify`: unit tests passed, but Failsafe could not start either Testcontainers integration class (16 tests reported errors) because Testcontainers could not find a usable Docker engine. The Docker engine pipe `//./pipe/docker_engine` was absent.
- `docker compose config --quiet`: passed using the installed Compose v2.40.2 plugin.
- `docker compose ps`: blocked because the Docker engine was unavailable. `docker compose up -d --build`, Docker image builds, and the real-database Playwright E2E were therefore not run.

## Remaining issues

- Start Docker Desktop/its Linux engine, then run `docker compose up -d --build`, `docker compose ps`, `cd backend; .\mvnw.cmd clean verify`, and `cd frontend; npm run test:e2e:chrome` to verify the database-backed journey.
- The full registration-to-dashboard flow has not been observed against PostgreSQL; registration, login/refresh persistence, organization membership persistence, and real dashboard values remain unverified at runtime.
- Production deployment settings still need environment-specific validation (HTTPS origin, secure cookies, exact CORS origins, production API URL, and managed database/Redis connectivity). No production deployment was attempted.
