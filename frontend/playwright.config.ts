import { defineConfig, devices } from "@playwright/test";

/**
 * E2E runs against the real UI + the real backend.
 *
 * Requirements (see README / docs/DEVELOPMENT_SETUP.md):
 * - backend reachable at the configured API base (dev default
 *   http://localhost:8080; see e2e/api-base.ts for resolution) and
 *   CORS allowing the E2E origin (http://localhost:3101)
 * - the frontend served by a **production build** (`next build` + the
 *   standalone server) on a dedicated E2E port
 *
 * Why a dedicated port (3101): the development frontend runs on 3001 and the
 * Docker stack publishes 3001 as well. Reusing either of them would mean
 * testing whatever happened to be running — possibly a `next dev` instance
 * with on-demand compilation, or a stale Docker image. The E2E server owns
 * its port, so the suite always tests the build it just produced.
 *
 * Why a production build and not `next dev`:
 * the dev server compiles routes on demand, so the first visit to a route can
 * take many seconds. Two failures follow from that, and neither is an
 * application defect:
 *   1. a click can land before React has hydrated, so the browser performs a
 *      native HTML form submission instead of the JavaScript handler;
 *   2. a navigation can take longer than the assertion timeout, so the URL
 *      assertion fails while the app is in fact working.
 * A production build has every route pre-compiled and hydrates immediately,
 * which makes the suite deterministic and tests what users actually get.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false, // auth tests share one backend — keep serial
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "list",
  // Assertions wait for real HTTP round-trips against a real backend. Two of
  // them depend on the session bootstrap (POST /auth/refresh -> GET /auth/me)
  // before the app can decide where to navigate, so the budget has to cover a
  // cold container start and a loaded CI machine. This is an assertion budget,
  // not a sleep: no test waits a fixed amount of time for an outcome, and a
  // redirect that never happens still fails.
  expect: {
    timeout: 20_000,
  },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3101",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    timezoneId: "Africa/Addis_Ababa",
  },
  projects: [
    {
      name: "chrome",
      use: { ...devices["Desktop Chrome"], channel: "chrome" },
    },
    // Keep chromium project for CI headless fallback
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
      // CI: headed=false by default; override with CI env if needed
    },
  ],
  // CI starts the server itself (it needs the backend up first).
  webServer: process.env.CI
    ? undefined
    : {
        // Build once, then serve the real production bundle.
        command: "npm run e2e:server",
        url: "http://localhost:3101",
        // Never reuse an already-running server: it may be a `next dev`
        // instance (slow, on-demand compilation) or a stale build.
        reuseExistingServer: false,
        timeout: 300_000,
      },
});
