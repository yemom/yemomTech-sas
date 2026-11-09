import { expect, test } from "@playwright/test";

const PASSWORD = "Correct-Horse-9!";

test("multi-tenant boundary: workspace isolation between different accounts", async ({ page, browser }) => {
  test.setTimeout(120_000);

  const orgAEmail = `org-a-${Date.now()}@example.et`;
  const orgBEmail = `org-b-${Date.now()}@example.et`;

  // 1. Create Org A
  await page.goto("/register");
  await page.getByLabel("First name").fill("Tenant");
  await page.getByLabel("Last name").fill("Alpha");
  await page.getByLabel("Email").fill(orgAEmail);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByLabel("Organization name").fill("Alpha Corporation");
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page).toHaveURL(/\/workspace$/);
  await expect(page.getByRole("heading", { name: "Alpha Corporation" })).toBeVisible();

  // Register website in Org A
  await page.getByRole("link", { name: "Websites" }).click();
  await expect(page).toHaveURL(/\/workspace\/websites$/);
  await page.getByRole("button", { name: "Register website" }).first().click();
  await page.getByLabel("Name").fill("Alpha Web");
  await page.getByLabel("URL").fill("https://alpha-corp.example.com");
  await page.getByRole("button", { name: "Register website" }).last().click();
  await expect(page.getByText("alpha-corp.example.com")).toBeVisible();

  // 2. In a separate isolated browser context, create Org B
  const contextB = await browser.newContext();
  const pageB = await contextB.newPage();

  await pageB.goto("/register");
  await pageB.getByLabel("First name").fill("Tenant");
  await pageB.getByLabel("Last name").fill("Beta");
  await pageB.getByLabel("Email").fill(orgBEmail);
  await pageB.getByLabel("Password").fill(PASSWORD);
  await pageB.getByRole("button", { name: "Create account" }).click();

  await expect(pageB).toHaveURL(/\/onboarding$/);
  await pageB.getByLabel("Organization name").fill("Beta Enterprise");
  await pageB.getByRole("button", { name: "Create workspace" }).click();
  await expect(pageB).toHaveURL(/\/workspace$/);
  await expect(pageB.getByRole("heading", { name: "Beta Enterprise" })).toBeVisible();

  // Org B navigates to websites: MUST NOT see Org A's website
  await pageB.getByRole("link", { name: "Websites" }).click();
  await expect(pageB).toHaveURL(/\/workspace\/websites$/);
  await expect(pageB.getByText("alpha-corp.example.com")).not.toBeVisible();
  await expect(pageB.getByRole("heading", { name: "No websites yet" })).toBeVisible();

  await contextB.close();
});
