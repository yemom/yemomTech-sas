import { defineConfig, devices } from "@playwright/test";

/**
 * E2E runs against the dev server on 3001; the backend must be running on
 * 8082 with the `dev` profile (expose-dev-token enabled) and CORS allowing
 * http://localhost:3001.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false, // auth tests share one backend — keep serial
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3001",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    channel: "chrome", // Use actual installed Google Chrome instead of bundled Chromium
    timezoneId: "Africa/Addis_Ababa",
  },
  projects: [
    {
      name: "chrome",
      use: { ...devices["Desktop Chrome"] },
    },
    // Keep chromium project for CI headless fallback
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
      // CI: headed=false by default; override with CI env if needed
    },
  ],
  webServer: process.env.CI
    ? undefined
    : {
        command: "npm run dev",
        url: "http://localhost:3001",
        reuseExistingServer: true,
        timeout: 60_000,
      },
});
