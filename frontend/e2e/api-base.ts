/**
 * The API origin the E2E specs use for direct HTTP calls
 * (`page.request.get/post` against `/auth/*`, `/organizations/...`).
 *
 * Resolution order — the first defined value wins:
 *
 * 1. `E2E_API_BASE_URL` — explicit override for a run (used by CI, which
 *    knows the backend address it started).
 * 2. `NEXT_PUBLIC_API_BASE_URL` — the same variable the production build
 *    bakes into the app. When the specs run against a locally built bundle,
 *    the value comes from `frontend/.env.local`, so the tests automatically
 *    target the backend the app itself talks to (e.g. a machine where host
 *    port 8080 is occupied and the stack is published on 8082).
 * 3. `http://localhost:8080/api/v1` — the documented development default.
 *
 * Keeping this in ONE place mirrors `src/lib/api/api-base.ts` on the app
 * side: a hardcoded port duplicated across specs goes stale the moment the
 * backend moves, and the failure looks like an app defect when it is only a
 * test-environment mismatch.
 */
import { readFileSync } from "node:fs";
import path from "node:path";

const DEV_DEFAULT = "http://localhost:8080/api/v1";

function fromLocalEnvFile(): string | undefined {
  // The Playwright process does not load .env files (only `next build` does,
  // inside the webServer), so read the same file the build reads.
  const file = path.join(__dirname, "..", ".env.local");
  try {
    const line = readFileSync(file, "utf8")
      .split("\n")
      .map((l) => l.trim())
      .find((l) => l.startsWith("NEXT_PUBLIC_API_BASE_URL="));
    const value = line?.slice("NEXT_PUBLIC_API_BASE_URL=".length).trim();
    return value || undefined;
  } catch {
    return undefined; // no .env.local — fall through to the default
  }
}

function resolveApiBase(): string {
  if (process.env.E2E_API_BASE_URL) return process.env.E2E_API_BASE_URL;
  if (process.env.NEXT_PUBLIC_API_BASE_URL) return process.env.NEXT_PUBLIC_API_BASE_URL;
  return fromLocalEnvFile() ?? DEV_DEFAULT;
}

export const API_BASE = resolveApiBase();
