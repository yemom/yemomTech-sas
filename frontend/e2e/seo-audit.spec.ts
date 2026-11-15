import { expect, test, type Page } from "@playwright/test";

const PASSWORD = "Correct-Horse-9!";

/**
 * The API origin. The browser app and the API are different origins during
 * E2E (the app is served by the production build on its own port, the API by
 * the backend), so a direct API call has to target the backend explicitly.
 */
const API_BASE = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";

/**
 * Website registration and the SEO audit workflow.
 *
 * This spec asserts real behaviour, not just navigation: the audit is
 * actually triggered through the API, the audit row appears, and the
 * persisted state is checked. An earlier version of this file skipped the
 * audit assertions when the button was not visible, which meant the test
 * could pass while the feature was broken.
 */
test("user can register website and run an SEO audit", async ({ page }) => {
  test.setTimeout(120_000);
  const email = `e2e-seo-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.et`;

  // Register & create workspace
  await page.goto("/register");
  await page.getByLabel("First name").fill("Seo");
  await page.getByLabel("Last name").fill("Tester");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByLabel("Organization name").fill("SEO Test Enterprise");
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page).toHaveURL(/\/workspace$/);

  // Navigate to Websites
  await page.getByRole("link", { name: "Websites" }).click();
  await expect(page).toHaveURL(/\/workspace\/websites$/);

  // Register a public website
  await page.getByRole("button", { name: "Register website" }).first().click();
  await page.getByLabel("Name").fill("Example Domain");
  await page.getByLabel("URL").fill("https://example.com");
  await page.getByRole("button", { name: "Register website" }).last().click();

  await expect(page.getByText("example.com")).toBeVisible();

  // Open the audits page. The button must exist — this is asserted, not
  // skipped: a missing action would otherwise make the whole audit flow
  // untested while the spec still reported success.
  const auditButton = page.getByRole("button", { name: "Audits" }).first();
  await expect(auditButton).toBeVisible();
  await auditButton.click();

  await expect(page).toHaveURL(/\/workspace\/websites\/[^/]+\/audits$/);
  await expect(page.getByRole("heading", { name: "SEO Audit History" })).toBeVisible();

  // A brand new website has no audits: the empty state must say so.
  await expect(page.getByText("No audits conducted yet")).toBeVisible();

  // Trigger a real audit through the UI.
  await page.getByRole("button", { name: "Run New Audit" }).click();

  // The audit is persisted and the page reflects it: either the audit is
  // running, or it already completed.
  await expect(
    page.getByText("SEO Audit in progress").or(page.getByText(/COMPLETED|FAILED|PENDING|RUNNING/)).first(),
  ).toBeVisible({ timeout: 20_000 });

  // The audit really exists in the backend, not just in the UI.
  //
  // Note: /auth/refresh rotates the CSRF cookie server-side, so the token is
  // minted exactly once and reused. Minting it twice would make the second
  // call present a stale CSRF value and fail with 403.
  const token = await accessToken(page);
  const organizationId = await currentOrganizationId(page, token);
  const audits = await page.request.get(
    `${API_BASE}/organizations/${organizationId}/websites/${websiteIdFromUrl(page.url())}/audits`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(audits.status()).toBe(200);
  const body = await audits.json();
  expect(body.content.length).toBeGreaterThan(0);
});

/**
 * Obtain an access token for a direct API call.
 *
 * The access token lives in memory only (by design — never in localStorage),
 * so a test cannot read it from the page. The refresh cookie plus the
 * double-submit CSRF value can mint a fresh one, exactly as the app does.
 */
async function accessToken(page: Page): Promise<string> {
  const csrf = await page.evaluate(() => window.localStorage.getItem("YemomBizGrow_csrf"));
  if (!csrf) throw new Error("No CSRF token in localStorage.");
  const response = await page.request.post(`${API_BASE}/auth/refresh`, {
    headers: { "X-CSRF-TOKEN": csrf },
  });
  if (!response.ok()) {
    throw new Error(`Could not mint an access token: HTTP ${response.status()}`);
  }
  const body = await response.json();
  return body.accessToken as string;
}

/** Read the organization id from the API the page itself uses. */
async function currentOrganizationId(page: Page, token: string): Promise<string> {
  const response = await page.request.get(`${API_BASE}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await response.json();
  const organizationId = body?.memberships?.[0]?.organizationId;
  if (typeof organizationId !== "string") {
    throw new Error("The signed-in user has no organization.");
  }
  return organizationId;
}

/** Pull the website id out of the /websites/{id}/audits URL. */
function websiteIdFromUrl(url: string): string {
  const match = url.match(/\/websites\/([^/]+)\/audits/);
  const websiteId = match?.[1];
  if (!websiteId) throw new Error(`No website id in ${url}`);
  return websiteId;
}
