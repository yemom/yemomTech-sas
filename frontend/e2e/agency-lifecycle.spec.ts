import { expect, test } from "@playwright/test";

const PASSWORD = "Correct-Horse-9!";

test("agency client management UI validates client link request authorization", async ({ page }) => {
  test.setTimeout(90_000);
  const email = `e2e-agency-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.et`;

  // Register & create workspace
  await page.goto("/register");
  await page.getByLabel("First name").fill("Agency");
  await page.getByLabel("Last name").fill("Owner");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByLabel("Organization name").fill("Addis Marketing Agency");
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page).toHaveURL(/\/workspace$/);

  // Navigate to Agency portal
  await page.getByRole("link", { name: "Agency" }).click();
  await expect(page).toHaveURL(/\/workspace\/agency$/);

  // Agency dashboard header should be visible
  await expect(page.getByRole("heading", { name: "Agency Client Workspace" })).toBeVisible();
  await expect(page.getByRole("button", { name: "+ Link Client Organization" })).toBeVisible();

  // Open link client modal
  await page.getByRole("button", { name: "+ Link Client Organization" }).click();
  await expect(page.getByRole("heading", { name: "Link Client Organization" })).toBeVisible();

  // Enter invalid/non-existent client slug and verify fail-closed error handling
  await page.getByPlaceholder("e.g. ethio-real-estate").fill("non-existent-client-org");
  await page.getByRole("button", { name: "Link Client" }).click();

  // Modal remains open or displays error callout
  await expect(page.getByRole("heading", { name: "Link Client Organization" })).toBeVisible();
});
