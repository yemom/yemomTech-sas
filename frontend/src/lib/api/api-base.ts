/**
 * The single place where the browser-facing API base URL is resolved.
 *
 * <p>`NEXT_PUBLIC_*` values are baked into the JavaScript bundle at build
 * time (Next.js replaces every `process.env.NEXT_PUBLIC_API_BASE_URL`
 * reference while building), so a production image built without the
 * variable can never be fixed by setting the variable in the hosting
 * environment — the frontend has to be rebuilt.</p>
 *
 * <p>The rules, in order:</p>
 * <ol>
 *   <li>An explicitly configured value is validated and used. An invalid
 *       value is a build error, in development too — a typo must not
 *       silently produce a frontend that talks to the wrong backend.</li>
 *   <li>Unset in development: falls back to the canonical local backend
 *       (http://localhost:8080/api/v1) with a console warning, so a fresh
 *       clone works out of the box.</li>
 *   <li>Unset in production: throws. `next build` prerenders the pages, so
 *       this fails the production build with an explicit message instead of
 *       silently baking localhost into a deployed bundle.</li>
 * </ol>
 *
 * <p>This module is the only file in the repository that may declare a local
 * fallback; `src/lib/api/api-base-url.test.ts` enforces that.</p>
 */

const DEV_FALLBACK = "http://localhost:8080/api/v1";

function isProduction(): boolean {
  return process.env.NODE_ENV === "production";
}

function validate(raw: string): string {
  const value = raw.trim();

  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error(
      `NEXT_PUBLIC_API_BASE_URL must be an absolute http(s) URL, got "${value}". ` +
        'Example: NEXT_PUBLIC_API_BASE_URL="https://api.example.com/api/v1".',
    );
  }

  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error(
      `NEXT_PUBLIC_API_BASE_URL must use http or https, got "${parsed.protocol}".`,
    );
  }
  if (parsed.search !== "" || parsed.hash !== "") {
    throw new Error(
      `NEXT_PUBLIC_API_BASE_URL must not contain a query string or fragment, got "${value}".`,
    );
  }
  if (parsed.username !== "" || parsed.password !== "") {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL must not contain URL credentials (user:password).",
    );
  }
  if (parsed.hostname === "") {
    throw new Error(
      `NEXT_PUBLIC_API_BASE_URL must include a host, got "${value}".`,
    );
  }
  if (value.endsWith("/")) {
    throw new Error(
      `NEXT_PUBLIC_API_BASE_URL must not end with a slash (the client appends paths), got "${value}".`,
    );
  }
  return value;
}

function resolveApiBaseUrl(): string {
  const raw = process.env.NEXT_PUBLIC_API_BASE_URL;

  if (raw === undefined || raw.trim() === "") {
    if (isProduction()) {
      throw new Error(
        "NEXT_PUBLIC_API_BASE_URL is not set. A production frontend must be " +
          "built with the real backend URL, for example: " +
          'NEXT_PUBLIC_API_BASE_URL="https://api.example.com/api/v1" npm run build. ' +
          "The value is baked into the browser bundle at build time; setting it " +
          "in the hosting environment alone has no effect on an already-built " +
          "frontend. Rebuild (and rebuild the Docker image) after changing it.",
      );
    }
    // Development only: a fresh clone with no .env works against the local
    // compose stack. The warning makes the fallback visible instead of
    // silent, so a misconfigured dev server is diagnosable from the console.
    console.warn(
      `[api] NEXT_PUBLIC_API_BASE_URL is not set; falling back to ${DEV_FALLBACK}. ` +
        "Set it in frontend/.env.local to target a different backend.",
    );
    return DEV_FALLBACK;
  }

  return validate(raw);
}

/** The base every frontend API request is sent to, e.g. `https://host/api/v1`. */
export const API_BASE = resolveApiBaseUrl();
