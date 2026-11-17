import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * Guards the API base URL configuration.
 *
 * <p>History, in order: the frontend used to carry two different local
 * fallbacks (8082 and 8080), then several files each carried the same 8080
 * fallback. Both shapes are dangerous: the first boots cleanly and talks to
 * a backend that is not running; the second makes "production must never
 * silently use localhost" unenforceable, because the fallback lives in
 * too many places to police.</p>
 *
 * <p>The architecture now is: exactly one module
 * (`src/lib/api/api-base.ts`) may declare a local fallback — and only for
 * development; a production build without `NEXT_PUBLIC_API_BASE_URL` fails
 * the build. This test reads the real files and pins that architecture.</p>
 */

const HERE = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = resolve(HERE, "..", "..", "..");
const REPO_ROOT = resolve(FRONTEND_ROOT, "..");

/** The one canonical local backend URL for the whole repository. */
const CANONICAL_API_BASE_URL = "http://localhost:8080/api/v1";

function read(...segments: string[]): string {
  return readFileSync(resolve(...segments), "utf-8");
}

describe("API base URL configuration", () => {
  it("declares the canonical dev fallback in exactly one place: api-base.ts", () => {
    const apiBase = read(FRONTEND_ROOT, "src", "lib", "api", "api-base.ts");
    expect(apiBase).toContain(`const DEV_FALLBACK = "${CANONICAL_API_BASE_URL}"`);
  });

  it("does not let the API client or next.config declare their own fallback", () => {
    const client = read(FRONTEND_ROOT, "src", "lib", "api", "client.ts");
    const nextConfig = read(FRONTEND_ROOT, "next.config.ts");
    const reportsPage = read(FRONTEND_ROOT, "src", "app", "workspace", "reports", "page.tsx");

    for (const [source, content] of [
      ["src/lib/api/client.ts", client],
      ["next.config.ts", nextConfig],
      ["src/app/workspace/reports/page.tsx", reportsPage],
    ] as const) {
      expect(content, `${source} must not hardcode the backend URL`).not.toContain(
        CANONICAL_API_BASE_URL,
      );
    }
    // next.config.ts must not inject a fallback through the `env` block —
    // that would silently defeat the production build guard in api-base.ts.
    expect(nextConfig).not.toMatch(
      /NEXT_PUBLIC_API_BASE_URL\s*:\s*process\.env\.NEXT_PUBLIC_API_BASE_URL\s*\?\?/,
    );
  });

  it("keeps the development-only fallback in the development config files", () => {
    const frontendEnvExample = read(FRONTEND_ROOT, ".env.example");
    const rootEnvExample = read(REPO_ROOT, ".env.example");
    const compose = read(REPO_ROOT, "docker-compose.yml");

    expect(frontendEnvExample).toContain(`NEXT_PUBLIC_API_BASE_URL=${CANONICAL_API_BASE_URL}`);
    expect(rootEnvExample).toContain(`NEXT_PUBLIC_API_BASE_URL=${CANONICAL_API_BASE_URL}`);
    // The compose stack is the local development stack; it passes the
    // localhost default itself, so the Dockerfile can require an explicit
    // value for every build.
    expect(compose).toMatch(/NEXT_PUBLIC_API_BASE_URL:-http:\/\/localhost:8080\/api\/v1/);
  });

  it("requires an explicit API URL in the production Docker build", () => {
    const dockerfile = read(REPO_ROOT, "infrastructure", "docker", "frontend.Dockerfile");
    // No silent default: the ARG must default to empty and the build must
    // fail with an explicit message when it is not provided.
    expect(dockerfile).toMatch(/^ARG NEXT_PUBLIC_API_BASE_URL=\s*$/m);
    expect(dockerfile).toMatch(/if \[ -z "\$NEXT_PUBLIC_API_BASE_URL" \]/);
  });

  it("never points at a stale backend port", () => {
    const files = [
      read(FRONTEND_ROOT, "src", "lib", "api", "api-base.ts"),
      read(FRONTEND_ROOT, "src", "lib", "api", "client.ts"),
      read(FRONTEND_ROOT, "next.config.ts"),
      read(REPO_ROOT, "docker-compose.yml"),
    ];
    for (const content of files) {
      expect(content, "must not use the retired port 8082").not.toContain(":8082");
    }
  });
});
