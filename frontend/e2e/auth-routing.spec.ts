import { expect, test, type Page } from "@playwright/test";

/**
 * Regression coverage for the authentication routing rules.
 *
 * | User condition                                        | Destination          |
 * |-------------------------------------------------------|----------------------|
 * | New user, no organization                             | /onboarding          |
 * | Existing user with an active organization             | /workspace           |
 * | Authenticated user visiting /login                    | /workspace|/onboarding |
 * | Authenticated user visiting /register                 | /workspace|/onboarding |
 * | Valid internal `next` parameter                       | the requested page   |
 * | Unauthenticated visitor on a protected route          | /login?next=...      |
 * | Login fails                                           | stays on /login      |
 * | Registration fails (duplicate email)                  | stays on /register   |
 */

const PASSWORD = "Correct-Horse-9!";

/** Same origin the frontend fetches from (see infrastructure/docker and CI). */
import { API_BASE } from "./api-base";

function uniqueEmail(prefix: string): string {
  return `e2e-${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.et`;
}

/** Register a fresh account and land on the onboarding page. */
async function registerUser(page: Page, email: string): Promise<void> {
  await page.goto("/register");
  await page.getByLabel("First name").fill("Routing");
  await page.getByLabel("Last name").fill("Tester");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/onboarding$/);
}

/** Create the organization and land on the workspace dashboard. */
async function createWorkspace(page: Page, name: string): Promise<void> {
  await page.getByLabel("Organization name").fill(name);
  await page.getByRole("button", { name: "Create workspace" }).click();
  await expect(page).toHaveURL(/\/workspace$/);
}

test("a new user without an organization is sent to onboarding", async ({ page }) => {
  test.setTimeout(90_000);
  await registerUser(page, uniqueEmail("route-new"));

  await expect(page.getByRole("heading", { name: "Create your workspace" })).toBeVisible();
  await expect(page).toHaveURL(/\/onboarding$/);
});

test("an existing user with an organization is sent to the workspace", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-existing");

  await registerUser(page, email);
  await createWorkspace(page, `Routing Existing ${Date.now()}`);

  // Sign out, then sign back in: the user now has an organization.
  await page.locator('button[aria-haspopup="menu"]:not([data-nextjs-dev-tools-button])').click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page).toHaveURL(/\/workspace$/);
  await expect(page.getByRole("heading", { name: /Routing Existing/ })).toBeVisible();
});

test("an authenticated user cannot stay on the login page", async ({ page }) => {
  test.setTimeout(90_000);
  await registerUser(page, uniqueEmail("route-auth-login"));

  await page.goto("/login");

  // The session is restored from the HttpOnly refresh cookie and the user is
  // forwarded to the onboarding page instead of being left on /login.
  await expect(page).toHaveURL(/\/onboarding$/);
});

test("an authenticated user cannot stay on the register page", async ({ page }) => {
  test.setTimeout(90_000);
  await registerUser(page, uniqueEmail("route-auth-register"));

  await page.goto("/register");

  await expect(page).toHaveURL(/\/onboarding$/);
});

test("a valid internal next parameter is honoured after login", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-next");

  await registerUser(page, email);
  await createWorkspace(page, `Routing Next ${Date.now()}`);

  await page.locator('button[aria-haspopup="menu"]:not([data-nextjs-dev-tools-button])').click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login/);

  // An unauthenticated visitor is redirected to login with a next parameter.
  await page.goto("/workspace/websites");
  await expect(page).toHaveURL(/\/login\?next=%2Fworkspace%2Fwebsites/);

  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  // The requested page is restored after authentication.
  await expect(page).toHaveURL(/\/workspace\/websites$/);
});

test("an external next parameter is rejected as an open-redirect vector", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-open-redirect");

  await registerUser(page, email);
  await createWorkspace(page, `Routing Open Redirect ${Date.now()}`);

  // Sign out first: an authenticated visitor is forwarded away from /login,
  // so the sign-in form would not even be there.
  await page.locator('button[aria-haspopup="menu"]:not([data-nextjs-dev-tools-button])').click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto("/login?next=https://evil.example.com/steal");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  // The user stays inside the application. The assertion is deliberately
  // port-agnostic: the E2E server runs on its own port, and the property
  // under test is "did not leave the app", not "which port did it use".
  await expect(page).toHaveURL(/\/(workspace|onboarding)(\?|$)/);
  expect(page.url()).not.toContain("evil.example.com");
});

test("a temporary /auth/me failure after registration does not break the journey", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-me-failure");

  // The account is created and the refresh cookie is issued, but reading the
  // profile fails. The user must not be told the registration failed (that
  // would push them into a duplicate-registration loop) and must not be
  // signed out.
  let failProfileReads = true;
  await page.route("**/api/v1/auth/me", async (route) => {
    if (failProfileReads) {
      await route.abort("failed");
      return;
    }
    await route.continue();
  });

  await page.goto("/register");
  await page.getByLabel("First name").fill("Routing");
  await page.getByLabel("Last name").fill("Tester");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  // The user still reaches onboarding...
  await expect(page).toHaveURL(/\/onboarding$/, { timeout: 20_000 });
  // ...and is NOT shown a registration failure.
  await expect(page.getByText("already exists")).not.toBeVisible();
  await expect(page.getByRole("heading", { name: "Create your workspace" })).toBeVisible();

  // The profile endpoint works again from here on.
  failProfileReads = false;

  // The account really was created: signing in with the same credentials
  // works. The session cookie is dropped first, otherwise the still-valid
  // session would bounce us away from /login before the form can be used.
  await page.context().clearCookies();
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page).toHaveURL(/\/(onboarding|workspace)/, { timeout: 20_000 });
  expect(page.url()).not.toContain("/login");
});

test("a duplicate registration keeps the user on the register page", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-duplicate");

  await registerUser(page, email);
  await createWorkspace(page, `Routing Duplicate ${Date.now()}`);

  // Sign out and try to register the same address again.
  await page.locator('button[aria-haspopup="menu"]:not([data-nextjs-dev-tools-button])').click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto("/register");
  await page.getByLabel("First name").fill("Routing");
  await page.getByLabel("Last name").fill("Tester");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  // A conflict is reported and the user is NOT navigated away.
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.getByText("An account with this email already exists")).toBeVisible();
});

test("the session survives a browser reload", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-reload");

  await registerUser(page, email);
  await createWorkspace(page, `Routing Reload ${Date.now()}`);

  await page.reload();

  // No redirect to /login: the refresh cookie restored the session.
  await expect(page).toHaveURL(/\/workspace$/);
  await expect(page.getByRole("heading", { name: /Routing Reload/ })).toBeVisible();
});

test("logout terminates the session and protected routes reject the visitor", async ({ page }) => {
  test.setTimeout(90_000);
  await registerUser(page, uniqueEmail("route-logout"));
  await createWorkspace(page, `Routing Logout ${Date.now()}`);

  await page.locator('button[aria-haspopup="menu"]:not([data-nextjs-dev-tools-button])').click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto("/workspace");
  await expect(page).toHaveURL(/\/login/);

  // Even a direct reload of a protected page must not restore the session.
  await page.goto("/workspace/websites");
  await expect(page).toHaveURL(/\/login/);
});

/**
 * The original defect behind "clicking Sign in does nothing" was the form
 * submitting natively as a document GET (`/login?email=...`), which exposed
 * the credentials in the URL. These tests pin the contract at the network
 * layer: the buttons must issue fetch POSTs to the API and no document
 * navigation may ever carry credentials.
 */
test("sign-in issues a fetch POST to the API, never a document navigation with credentials", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-net-login");

  /** Document navigations observed after the initial page load. */
  const documentNavigations: string[] = [];
  /** Requests seen against the auth endpoints. */
  const loginRequests: { method: string; url: string; type: string }[] = [];

  page.on("request", (request) => {
    if (request.resourceType() === "document") {
      documentNavigations.push(`${request.method()} ${request.url()}`);
    }
    if (request.url().includes("/auth/login")) {
      loginRequests.push({
        method: request.method(),
        url: request.url(),
        type: request.resourceType(),
      });
    }
  });

  await page.goto("/login");
  documentNavigations.length = 0; // ignore the deliberate navigation to /login

  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();

  // The address is not registered, so the backend answers 401 — the point is
  // the request shape, not a successful session.
  await expect(page.getByText("Incorrect email or password.")).toBeVisible();

  // Exactly one POST, sent as a fetch (not a document), to the API base the
  // deployment configures.
  expect(loginRequests).toHaveLength(1);
  const [loginRequest] = loginRequests;
  expect(loginRequest?.method).toBe("POST");
  expect(loginRequest?.type).toBe("fetch");
  expect(loginRequest?.url).toBe(`${API_BASE}/auth/login`);

  // No document navigation happened after the click, and no credential ever
  // reached a URL.
  expect(documentNavigations).toEqual([]);
  for (const navigation of [...documentNavigations, ...loginRequests.map((r) => r.url)]) {
    expect(navigation).not.toContain("email=");
    expect(navigation).not.toContain("password=");
  }
});

test("create-account issues a fetch POST to the API, never a document navigation with credentials", async ({ page }) => {
  test.setTimeout(90_000);
  const email = uniqueEmail("route-net-register");

  const documentNavigations: string[] = [];
  const registerRequests: { method: string; url: string; type: string }[] = [];

  page.on("request", (request) => {
    if (request.resourceType() === "document") {
      documentNavigations.push(`${request.method()} ${request.url()}`);
    }
    if (request.url().includes("/auth/register")) {
      registerRequests.push({
        method: request.method(),
        url: request.url(),
        type: request.resourceType(),
      });
    }
  });

  await page.goto("/register");
  documentNavigations.length = 0;

  await page.getByLabel("First name").fill("Network");
  await page.getByLabel("Last name").fill("Contract");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();

  // Registration succeeds and the provider navigates client-side.
  await expect(page).toHaveURL(/\/onboarding$/, { timeout: 20_000 });

  expect(registerRequests).toHaveLength(1);
  const [registerRequest] = registerRequests;
  expect(registerRequest?.method).toBe("POST");
  expect(registerRequest?.type).toBe("fetch");
  expect(registerRequest?.url).toBe(`${API_BASE}/auth/register`);

  // The credentials must never appear in any URL, and the only navigation
  // after the click is the client-side transition to /onboarding.
  for (const navigation of documentNavigations) {
    expect(navigation).not.toContain("email=");
    expect(navigation).not.toContain("password=");
    expect(navigation).not.toContain("/auth/register");
  }
});
