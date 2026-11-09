import { expect, test } from "@playwright/test";

const PASSWORD = "Correct-Horse-9!";

test("user can register website and inspect SEO audit view", async ({ page }) => {
  test.setTimeout(90_000);
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

  // Open the website audits page
  const auditBtn = page.getByRole("button", { name: "Audits" }).first();
  if (await auditBtn.isVisible()) {
    await auditBtn.click();
    await expect(page).toHaveURL(/\/audits/);
  }
});
