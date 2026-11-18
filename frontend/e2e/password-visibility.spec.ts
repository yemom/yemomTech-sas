import { expect, test } from "@playwright/test";

/**
 * Password visibility toggles: the field must stay masked by default, the
 * toggle must reveal/hide the same value without submitting the form or
 * navigating away (verified earlier in the unit suite, proven here in a
 * real browser against the production build).
 */
test("sign-in password can be shown and hidden without submitting", async ({ page }) => {
  await page.goto("/login");

  const password = page.getByLabel("Password");
  await expect(password).toHaveAttribute("type", "password");
  await password.fill("Some-Password-1!");

  await page.getByRole("button", { name: "Show text" }).click();
  await expect(password).toHaveAttribute("type", "text");
  await expect(password).toHaveValue("Some-Password-1!");
  await expect(page).toHaveURL(/\/login$/);

  await page.getByRole("button", { name: "Hide text" }).click();
  await expect(password).toHaveAttribute("type", "password");
  await expect(password).toHaveValue("Some-Password-1!");
});

test("registration password can be shown and hidden without submitting", async ({ page }) => {
  await page.goto("/register");

  const password = page.getByLabel("Password");
  await expect(password).toHaveAttribute("type", "password");
  await password.fill("Some-Password-1!");

  await page.getByRole("button", { name: "Show text" }).click();
  await expect(password).toHaveAttribute("type", "text");
  await expect(password).toHaveValue("Some-Password-1!");

  // The live password-rule checklist still updates while revealed.
  await expect(page.getByText("✓ at least 8 characters")).toBeVisible();
  await expect(page).toHaveURL(/\/register$/);

  await page.getByRole("button", { name: "Hide text" }).click();
  await expect(password).toHaveAttribute("type", "password");
  await expect(password).toHaveValue("Some-Password-1!");
});
