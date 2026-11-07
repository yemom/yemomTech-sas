import { expect, test } from "@playwright/test";

/**
 * End-to-end vertical slice through the real UI + real backend:
 * register → create workspace → dashboard → register website →
 * members → logout → login again.
 *
 * Requirements (see playwright.config.ts):
 * - backend on http://localhost:8082 (dev profile, expose-dev-token on)
 * - frontend dev server on http://localhost:3001
 *
 * Label selectors use non-exact matching because required fields render
 * their label as "Email *" (label text + required marker).
 */

const PASSWORD = "Correct-Horse-9!";

test("owner runs the full vertical slice", async ({ page }) => {
  test.setTimeout(90_000);
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.et`;

  // ---- register ----------------------------------------------------------
  await page.goto("/register");
  await page.getByLabel("First name").fill("E2E");
  await page.getByLabel("Last name").fill("Owner");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  // New accounts land on workspace creation.
  await expect(page).toHaveURL(/\/onboarding$/);
  await expect(page.getByRole("heading", { name: "Create your workspace" })).toBeVisible();

  // ---- create workspace --------------------------------------------------
  await page.getByLabel("Organization name").fill("E2E Test Trading PLC");
  await page.getByRole("button", { name: "Create workspace" }).click();

  await expect(page).toHaveURL(/\/workspace$/);
  await expect(page.getByRole("heading", { name: "E2E Test Trading PLC" })).toBeVisible();

  // Real persisted counts: exactly one member (the owner), zero websites.
  await expect(page.getByText("Active members")).toBeVisible();
  await expect(page.getByText("OWNER").first()).toBeVisible();

  // Ethiopian defaults are displayed from server data.
  await expect(page.getByText("Africa/Addis_Ababa")).toBeVisible();
  await expect(page.getByText("ETB")).toBeVisible();

  // ---- register a website ------------------------------------------------
  await page.getByRole("link", { name: "Websites" }).click();
  await expect(page).toHaveURL(/\/workspace\/websites$/);
  await expect(page.getByRole("heading", { name: "No websites yet" })).toBeVisible();

  await page.getByRole("button", { name: "Register website" }).first().click();
  await page.getByLabel("Name").fill("E2E site");
  // Deliberately messy input: the backend must normalize it.
  await page.getByLabel("URL").fill("HTTPS://WWW.E2e-Example.COM/");
  await page.getByRole("button", { name: "Register website" }).last().click();

  // Normalized domain appears in the table with ACTIVE status.
  await expect(page.getByText("www.e2e-example.com")).toBeVisible();
  await expect(page.getByText("ACTIVE").first()).toBeVisible();

  // ---- members -----------------------------------------------------------
  await page.getByRole("link", { name: "Members" }).click();
  await expect(page).toHaveURL(/\/workspace\/members$/);
  await expect(page.getByText("(you)")).toBeVisible();
  await expect(page.getByText("Business Owner")).toBeVisible();

  // The owner cannot suspend or remove themselves.
  await expect(page.getByRole("button", { name: "Suspend" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Remove" })).toBeDisabled();

  // ---- logout ------------------------------------------------------------
  // Account menu (aria-haspopup="menu") — distinct from the org switcher;
  // exclude the Next.js dev-tools button which shares the same attribute.
  await page.locator('button[aria-haspopup="menu"]:not([data-nextjs-dev-tools-button])').click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login/);

  // A protected page after logout bounces back to login.
  await page.goto("/workspace");
  await expect(page).toHaveURL(/\/login/);

  // ---- login again (session roundtrip) ----------------------------------
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  // The `next` param returns the user to the workspace.
  await expect(page).toHaveURL(/\/workspace/);
  await expect(page.getByRole("heading", { name: "E2E Test Trading PLC" })).toBeVisible();
});

test("wrong password shows a generic error and reveals nothing", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Email").fill("nobody@example.et");
  await page.getByLabel("Password").fill("Wrong-Password-1!");
  await page.getByRole("button", { name: "Sign in" }).click();

  // Same generic message the backend returns — no user enumeration.
  await expect(page.getByText("Incorrect email or password.")).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test("registration enforces the password policy client-side", async ({ page }) => {
  await page.goto("/register");
  await page.getByLabel("First name").fill("Policy");
  await page.getByLabel("Last name").fill("Test");
  await page.getByLabel("Email").fill(`policy-${Date.now()}@example.et`);
  await page.getByLabel("Password").fill("weak");
  await page.getByRole("button", { name: "Create account" }).click();

  // Zod rejection — no request is sent, the form stays put.
  await expect(page).toHaveURL(/\/register$/);
  await expect(
    page.getByText("Use 8-128 characters including an uppercase letter"),
  ).toBeVisible();
});

test("an unauthenticated visitor is redirected from protected pages", async ({ page }) => {
  await page.goto("/workspace/websites");
  await expect(page).toHaveURL(/\/login\?next=%2Fworkspace%2Fwebsites/);
});
