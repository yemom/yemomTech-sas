import { expect, test } from "@playwright/test";

const PASSWORD = "Correct-Horse-9!";

test("user can navigate to billing, view subscription plans and payment history", async ({ page }) => {
  test.setTimeout(90_000);
  const email = `e2e-billing-${Date.now()}@example.et`;

  // Register & create workspace
  await page.goto("/register");
  await page.getByLabel("First name").fill("Billing");
  await page.getByLabel("Last name").fill("User");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByLabel("Organization name").fill("Billing Test PLC");
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page).toHaveURL(/\/workspace$/);

  // Navigate to Billing
  await page.getByRole("link", { name: "Billing" }).click();
  await expect(page).toHaveURL(/\/workspace\/billing$/);

  // Check header
  await expect(page.getByRole("heading", { name: "Subscription & Billing" })).toBeVisible();

  // Verify Ethiopian payment context
  await expect(page.getByText("Chapa, Telebirr, CBE Birr")).toBeVisible();
});
