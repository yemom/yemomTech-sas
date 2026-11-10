import { expect, test } from "@playwright/test";

const PASSWORD = "Correct-Horse-9!";

test("user can navigate to AI recommendations and assistant interface", async ({ page }) => {
  test.setTimeout(90_000);
  const email = `e2e-ai-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.et`;

  // Register & create workspace
  await page.goto("/register");
  await page.getByLabel("First name").fill("Ai");
  await page.getByLabel("Last name").fill("Tester");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByLabel("Organization name").fill("AI Innovate PLC");
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page).toHaveURL(/\/workspace$/);

  // Navigate to AI Insights & Bot
  await page.getByRole("link", { name: "AI Insights & Bot" }).click();
  await expect(page).toHaveURL(/\/workspace\/ai$/);

  // Verify AI header is visible
  await expect(page.getByRole("heading", { name: "AI Business Insights & Chatbot" })).toBeVisible();
});
