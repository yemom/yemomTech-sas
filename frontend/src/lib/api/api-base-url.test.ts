import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * Guards the API base URL configuration.
 *
 * <p>The frontend used to carry two different local fallbacks: {@code 8082} in
 * {@code next.config.ts} and {@code 8080} in the API client. A developer who
 * started the app without an explicit {@code NEXT_PUBLIC_API_BASE_URL} therefore
 * got a frontend that booted cleanly and then talked to a backend that was not
 * running — a failure that looks like an application bug and is not one.</p>
 *
 * <p>This test reads the real configuration files and asserts they all agree
 * on one canonical URL, so the inconsistency cannot come back silently.</p>
 */

const HERE = dirname(fileURLToPath(import.meta.url));
const FRONTEND_ROOT = resolve(HERE, "..", "..", "..");
const REPO_ROOT = resolve(FRONTEND_ROOT, "..");

/** The one canonical local backend URL for the whole repository. */
const CANONICAL_API_BASE_URL = "http://localhost:8080/api/v1";

function read(...segments: string[]): string {
  return readFileSync(resolve(...segments), "utf-8");
}

/** Every fallback/default the repository declares for the API base URL. */
function declaredApiBaseUrls(): { source: string; url: string }[] {
  const nextConfig = read(FRONTEND_ROOT, "next.config.ts");
  const apiClient = read(FRONTEND_ROOT, "src", "lib", "api", "client.ts");
  const frontendEnvExample = read(FRONTEND_ROOT, ".env.example");
  const rootEnvExample = read(REPO_ROOT, ".env.example");
  const compose = read(REPO_ROOT, "docker-compose.yml");

  return [
    {
      source: "next.config.ts",
      url: nextConfig.match(/NEXT_PUBLIC_API_BASE_URL:\s*\n?\s*process\.env\.NEXT_PUBLIC_API_BASE_URL\s*\?\?\s*"([^"]+)"/)?.[1]
        ?? "",
    },
    {
      source: "src/lib/api/client.ts",
      url: apiClient.match(/NEXT_PUBLIC_API_BASE_URL\s*\?\?\s*"([^"]+)"/)?.[1] ?? "",
    },
    {
      source: "frontend/.env.example",
      url: frontendEnvExample.match(/NEXT_PUBLIC_API_BASE_URL=(.+)/)?.[1]?.trim() ?? "",
    },
    {
      source: ".env.example",
      url: rootEnvExample.match(/NEXT_PUBLIC_API_BASE_URL=(.+)/)?.[1]?.trim() ?? "",
    },
    {
      source: "docker-compose.yml",
      url: compose.match(/NEXT_PUBLIC_API_BASE_URL:-\s*(http[^}\s]+)/)?.[1] ?? "",
    },
  ];
}

describe("API base URL configuration", () => {
  it("declares a fallback in every configuration file", () => {
    for (const entry of declaredApiBaseUrls()) {
      expect(entry.url, `${entry.source} must declare an API base URL`).not.toBe("");
    }
  });

  it("uses one canonical local backend URL everywhere", () => {
    for (const entry of declaredApiBaseUrls()) {
      expect(entry.url, `${entry.source} must point at the canonical backend`).toBe(
        CANONICAL_API_BASE_URL,
      );
    }
  });

  it("never points at a stale backend port", () => {
    for (const entry of declaredApiBaseUrls()) {
      expect(entry.url, `${entry.source} must not use the retired port 8082`).not.toContain(
        ":8082",
      );
    }
  });

  it("keeps the browser fallback and the bundler fallback identical", () => {
    const declared = declaredApiBaseUrls();
    const nextConfig = declared.find((entry) => entry.source === "next.config.ts");
    const apiClient = declared.find((entry) => entry.source === "src/lib/api/client.ts");
    expect(nextConfig?.url).toBe(apiClient?.url);
  });
});
