/**
 * Captures visual evidence of the registration → onboarding → workspace
 * journey against the running Docker stack.
 *
 * Usage:  node scripts/capture-evidence.mjs
 * Output: docs/evidence/*.png
 */
import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const FRONTEND_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const OUT_DIR = join(FRONTEND_ROOT, "..", "docs", "evidence");
const BASE_URL = process.env.EVIDENCE_BASE_URL ?? "http://localhost:3001";
const PASSWORD = "Correct-Horse-9!";

mkdirSync(OUT_DIR, { recursive: true });

const email = `evidence-${Date.now()}@example.et`;
const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await context.newPage();

const consoleErrors = [];
page.on("console", (m) => {
  if (m.type() === "error") consoleErrors.push(m.text());
});
page.on("pageerror", (e) => consoleErrors.push(`pageerror: ${e.message}`));

const requests = [];
page.on("request", (r) => requests.push(`${r.method()} ${r.url()}`));

// 1. Registration form
await page.goto(`${BASE_URL}/register`, { waitUntil: "networkidle" });
await page.getByLabel("First name").fill("Evidence");
await page.getByLabel("Last name").fill("Owner");
await page.getByLabel("Email").fill(email);
await page.getByLabel("Password").fill(PASSWORD);
await page.screenshot({ path: join(OUT_DIR, "01-register-form.png") });

// 2. Submit -> must land on /onboarding
await page.getByRole("button", { name: "Create account" }).click();
await page.waitForURL(/\/onboarding$/, { timeout: 15_000 });
await page.waitForLoadState("networkidle");
await page.screenshot({ path: join(OUT_DIR, "02-onboarding-after-registration.png") });

// 3. Create the workspace -> must land on /workspace
await page.getByLabel("Organization name").fill("Evidence Trading PLC");
await page.getByRole("button", { name: "Create workspace" }).click();
await page.waitForURL(/\/workspace$/, { timeout: 15_000 });
// The dashboard content arrives through a client-side query, so wait for the
// real persisted values instead of the page "load" event.
await page.getByRole("heading", { name: "Evidence Trading PLC" }).waitFor({ timeout: 15_000 });
await page.getByText("Active members").waitFor({ timeout: 15_000 });
await page.getByText("Africa/Addis_Ababa").waitFor({ timeout: 15_000 });
await page.screenshot({ path: join(OUT_DIR, "03-workspace-dashboard.png") });

// 4. Members page
await page.getByRole("link", { name: "Members" }).click();
await page.waitForURL(/\/workspace\/members$/, { timeout: 15_000 });
await page.getByText("(you)").waitFor({ timeout: 15_000 });
await page.screenshot({ path: join(OUT_DIR, "04-members.png") });

// 5. Session survives a reload
await page.reload({ waitUntil: "networkidle" });
await page.screenshot({ path: join(OUT_DIR, "05-session-after-reload.png") });

const urlAfterReload = page.url();

// 6. Logout -> protected routes reject the visitor
await page.locator('button[aria-haspopup="menu"]').first().click();
await page.getByRole("button", { name: "Sign out" }).click();
await page.waitForURL(/\/login/, { timeout: 15_000 });
await page.goto(`${BASE_URL}/workspace`, { waitUntil: "networkidle" });
await page.screenshot({ path: join(OUT_DIR, "06-protected-route-after-logout.png") });

const apiCalls = requests.filter((r) => r.includes("/api/v1/"));
const nativeFormPosts = apiCalls.filter((r) => r.startsWith("POST"));

console.log("=== EVIDENCE SUMMARY ===");
console.log("email                :", email);
console.log("url after reload     :", urlAfterReload);
console.log("url after logout     :", page.url());
console.log("api calls            :", apiCalls.length);
console.log("api POST calls       :", nativeFormPosts.length, nativeFormPosts);
console.log("console errors       :", consoleErrors.length ? consoleErrors : "none");

await browser.close();
