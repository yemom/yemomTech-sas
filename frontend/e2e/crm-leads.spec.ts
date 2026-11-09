import { expect, test } from "@playwright/test";

const PASSWORD = "Correct-Horse-9!";

test("user can navigate CRM leads, view pipeline and stages, and create a lead", async ({ page }) => {
  test.setTimeout(90_000);
  const email = `e2e-crm-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.et`;

  // Register & create workspace
  await page.goto("/register");
  await page.getByLabel("First name").fill("Crm");
  await page.getByLabel("Last name").fill("Tester");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByLabel("Organization name").fill("CRM Test PLC");
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page).toHaveURL(/\/workspace$/);

  // Navigate to Leads / CRM
  await page.getByRole("link", { name: "Leads" }).click();
  await expect(page).toHaveURL(/\/workspace\/leads$/);

  // Check CRM header and metrics
  await expect(page.getByRole("heading", { name: "Leads & Sales Pipeline" })).toBeVisible();
  await expect(page.getByText("Total Pipeline Value")).toBeVisible();
  await expect(page.getByText("Total Leads Tracked")).toBeVisible();

  // Create lead via UI modal
  await page.getByRole("button", { name: "+ Add New Lead" }).click();
  await expect(page.getByRole("heading", { name: "Add New Lead" })).toBeVisible();

  await page.getByLabel("First name").fill("Abebe");
  await page.getByLabel("Last name").fill("Bikila");
  await page.getByLabel("Email").fill("abebe@olympics.et");
  await page.getByLabel("Phone number").fill("0911223344");
  await page.getByLabel("Company name").fill("Marathon Athletics PLC");

  await page.getByRole("button", { name: "Save Lead" }).click();

  // Verify created lead is visible in board
  await expect(page.getByText("Abebe Bikila")).toBeVisible();
});
