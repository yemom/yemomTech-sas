import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Behaviour of the API base URL resolver.
 *
 * <p>The production guard is the point: a production build without
 * `NEXT_PUBLIC_API_BASE_URL` must fail (during `next build`, which prerenders
 * the pages and evaluates this module) instead of silently baking
 * localhost into a deployed bundle. Development keeps a loud fallback so a
 * fresh clone works against the local compose stack.</p>
 *
 * <p>The module resolves at import time, so each test re-imports it with a
 * clean module registry and a controlled environment.</p>
 */

const ORIGINAL_ENV = { ...process.env };

/** Re-import the module with exactly the given environment variables. */
async function importWith(env: Record<string, string | undefined>) {
  vi.resetModules();
  // Node turns `process.env.X = undefined` into the STRING "undefined", so
  // unset keys must be deleted, not assigned.
  const proc = process.env as unknown as Record<string, string | undefined>;
  for (const key of ["NODE_ENV", "NEXT_PUBLIC_API_BASE_URL"]) {
    if (env[key] !== undefined) proc[key] = env[key];
    else delete proc[key];
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (await import("./api-base")) as any;
}

beforeEach(() => {
  vi.spyOn(console, "warn").mockImplementation(() => {});
});

afterEach(() => {
  vi.restoreAllMocks();
  const proc = process.env as unknown as Record<string, string | undefined>;
  proc.NODE_ENV = ORIGINAL_ENV.NODE_ENV;
  proc.NEXT_PUBLIC_API_BASE_URL = ORIGINAL_ENV.NEXT_PUBLIC_API_BASE_URL;
});

describe("API base URL resolution", () => {
  it("uses an explicitly configured URL as-is", async () => {
    const mod = await importWith({
      NODE_ENV: "production",
      NEXT_PUBLIC_API_BASE_URL: "https://api.example.com/api/v1",
    });
    expect(mod.API_BASE).toBe("https://api.example.com/api/v1");
  });

  it("accepts a configured URL in development too", async () => {
    const mod = await importWith({
      NODE_ENV: "development",
      NEXT_PUBLIC_API_BASE_URL: "http://192.168.1.10:8080/api/v1",
    });
    expect(mod.API_BASE).toBe("http://192.168.1.10:8080/api/v1");
  });

  it("falls back to localhost in development, with a visible warning", async () => {
    const mod = await importWith({
      NODE_ENV: "development",
      NEXT_PUBLIC_API_BASE_URL: undefined,
    });
    expect(mod.API_BASE).toBe("http://localhost:8080/api/v1");
    // The fallback is loud: a misconfigured dev server must be diagnosable.
    expect(console.warn).toHaveBeenCalledWith(expect.stringContaining("NEXT_PUBLIC_API_BASE_URL"));
  });

  it("refuses to fall back to localhost in production", async () => {
    await expect(
      importWith({ NODE_ENV: "production", NEXT_PUBLIC_API_BASE_URL: undefined }),
    ).rejects.toThrow(/NEXT_PUBLIC_API_BASE_URL is not set/);
  });

  it("treats a whitespace-only value as unset (and refuses in production)", async () => {
    await expect(
      importWith({ NODE_ENV: "production", NEXT_PUBLIC_API_BASE_URL: "   " }),
    ).rejects.toThrow(/NEXT_PUBLIC_API_BASE_URL is not set/);
  });

  it("rejects a non-absolute URL in development too", async () => {
    await expect(
      importWith({ NODE_ENV: "development", NEXT_PUBLIC_API_BASE_URL: "/api/v1" }),
    ).rejects.toThrow(/must be an absolute http\(s\) URL/);
  });

  it("rejects a URL without a protocol", async () => {
    await expect(
      importWith({
        NODE_ENV: "production",
        NEXT_PUBLIC_API_BASE_URL: "api.example.com/api/v1",
      }),
    ).rejects.toThrow(/must be an absolute http\(s\) URL/);
  });

  it("rejects non-http protocols", async () => {
    await expect(
      importWith({ NODE_ENV: "production", NEXT_PUBLIC_API_BASE_URL: "ftp://api.example.com" }),
    ).rejects.toThrow(/must use http or https/);
  });

  it("rejects a trailing slash", async () => {
    await expect(
      importWith({
        NODE_ENV: "production",
        NEXT_PUBLIC_API_BASE_URL: "https://api.example.com/api/v1/",
      }),
    ).rejects.toThrow(/must not end with a slash/);
  });

  it("rejects a query string and a fragment", async () => {
    await expect(
      importWith({
        NODE_ENV: "production",
        NEXT_PUBLIC_API_BASE_URL: "https://api.example.com/api/v1?x=1",
      }),
    ).rejects.toThrow(/must not contain a query string or fragment/);
    await expect(
      importWith({
        NODE_ENV: "production",
        NEXT_PUBLIC_API_BASE_URL: "https://api.example.com/api/v1#frag",
      }),
    ).rejects.toThrow(/must not contain a query string or fragment/);
  });

  it("rejects embedded URL credentials", async () => {
    await expect(
      importWith({
        NODE_ENV: "production",
        NEXT_PUBLIC_API_BASE_URL: "https://user:pass@api.example.com/api/v1",
      }),
    ).rejects.toThrow(/must not contain URL credentials/);
  });
});
