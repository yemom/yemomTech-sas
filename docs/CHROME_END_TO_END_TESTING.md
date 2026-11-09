# YemomBizGrow — Chrome End-to-End Testing & Verification Guide

This guide describes how to run automated Playwright browser tests and manual end-to-end verification workflows in Google Chrome for the **YemomBizGrow** multi-tenant SaaS platform.

---

## 1. Prerequisites & Environment Setup

Ensure the following tools and runtimes are installed:
- **Google Chrome** (latest stable release)
- **Node.js 20+** and **npm**
- **Java 21 JDK** (Temurin or Oracle)
- **Docker & Docker Compose** (for PostgreSQL and Redis services)

### Step 1: Start Infrastructure Services
```powershell
docker compose up -d postgres redis
```

### Step 2: Start the Backend (Port 8082, Dev Profile)
```powershell
cd backend
.\mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
*Note: The `dev` profile enables local CORS for `http://localhost:3001` and enables test token endpoints.*

### Step 3: Start the Frontend (Port 3001)
```powershell
cd frontend
npm run dev
```

---

## 2. Automated End-to-End Tests via Playwright

Run the automated Chromium E2E suites:

```powershell
cd frontend

# Install Playwright browser binaries (one-time setup)
npx playwright install chromium

# Run all E2E tests headlessly
npm run e2e

# Run with headed Google Chrome UI and step inspection
npx playwright test --project=chromium --headed

# Open the HTML test execution report
npx playwright show-report
```

### Automated Test Coverage
- `vertical-slice.spec.ts`: End-to-end registration, workspace creation, website registration, member listing, session logout and re-authentication.
- `seo-audit.spec.ts`: Website domain registration, URL normalization, and SEO audit dashboard view.
- `crm-leads.spec.ts`: CRM pipeline navigation, lead tracking, and pipeline stage metrics.
- `agency-lifecycle.spec.ts`: Agency client management view, client linking request authorization checks.
- `ai-chatbot.spec.ts`: AI growth advisor view, offline fallback checks, and recommendation navigation.

---

## 3. Manual Chrome End-to-End Test Matrix

Follow these step-by-step instructions to verify each core feature in Google Chrome:

### Scenario 1: Authentication & Session Lifecycle
1. Open Google Chrome and navigate to `http://localhost:3001/register`.
2. Enter First Name, Last Name, Email (`owner@testbiz.et`), and a strong password (`Correct-Horse-9!`).
3. Click **Create account**.
4. Verify immediate redirect to `/onboarding`. Enter organization name: `Addis Tech Solutions PLC`.
5. Click **Create workspace**. Verify redirect to `/workspace` dashboard.
6. Verify persistent session cookies in Chrome DevTools (**Application → Cookies**): `refresh_token` (HttpOnly, SameSite=Strict).
7. Click the top-right account menu, click **Sign out**, and verify redirect to `/login`.
8. Attempt accessing `/workspace` while logged out; verify redirect back to `/login`.
9. Log in again with valid credentials; verify seamless return to `/workspace`.

### Scenario 2: Multi-Tenant Workspace & Role-Based Access Control (RBAC)
1. In the workspace, navigate to `/workspace/members`.
2. Verify the current user is listed as `OWNER` with `(you)` badge.
3. Verify that the **Suspend** and **Remove** buttons are disabled for the owner to prevent self-lockout.
4. Invite a new team member with role `MEMBER` via email `analyst@testbiz.et`.
5. Log in as the new member in a Chrome Incognito window.
6. Verify that administrative settings (e.g., billing, organization deletion, member removal) are inaccessible or disabled for non-owner roles.

### Scenario 3: Website Registration & SEO Crawl Audit
1. Navigate to `/workspace/websites`.
2. Click **Register website**.
3. In the modal, provide Name: `Corporate Portal` and messy URL: `HTTPS://WWW.EXAMPLE.COM/`.
4. Click **Register website**.
5. Verify that the website appears in the list with normalized domain: `example.com` and status `ACTIVE`.
6. Open the website audits page (`/workspace/websites/{id}/audits`).
7. Verify audit status and technical SEO scoring parameters. Note: Localhost or internal IP addresses (e.g. `http://127.0.0.1` or `http://169.254.169.254`) are strictly blocked by the SSRF Protection engine.

### Scenario 4: CRM Pipeline & Public Lead Capture Form
1. Navigate to `/workspace/leads`.
2. Verify the Kanban pipeline columns (`NEW`, `CONTACTED`, `QUALIFIED`, `PROPOSAL_SENT`, `WON`, `LOST`).
3. Create a new lead manually with name, Ethiopian phone number (`0911223344`), and estimated deal value.
4. Verify the phone number normalizes to standard `+251911223344`.
5. Submit a lead through the public API endpoint `/api/v1/public/leads/{orgSlug}`.
6. Test honeypot spam protection: send a request with the hidden honeypot field populated; verify the backend drops the lead without error.
7. Test IP rate limiting: send rapid requests from the same IP; verify HTTP 429 status response.

### Scenario 5: Agency Client Lifecycle & Access Level Enforcement
1. Register Organization A as an Agency (`Addis Marketing Agency`).
2. Register Organization B as a Client (`Finfinne Trading PLC`).
3. As the Agency, navigate to `/workspace/agency` and request a link to Organization B using its slug.
4. Verify the relationship starts in `PENDING` status.
5. In the Client session (Organization B), open `/workspace/agency` (or client approvals).
6. Verify the client can **Approve** or **Reject** the request.
7. Approve the connection. Verify relationship transitions to `ACTIVE`.
8. In the Agency session, verify access to permitted client metrics based on access level (`FULL`, `READ_ONLY`, or `MARKETING_ONLY`).
9. In the Client session, click **Revoke access**. Verify relationship transitions to `REVOKED`.
10. Verify that the Agency immediately loses access to client data. Attempting to reactivate a revoked relationship from the agency side returns HTTP 400.

### Scenario 6: Google Analytics 4 & Search Console Multi-Tenant Selection
1. Navigate to `/workspace/analytics`.
2. Select the registered website.
3. Click **Connect Google Account**.
4. Complete Google OAuth authorization (or observe proper error handling if `GOOGLE_CLIENT_ID` is not yet configured in environment).
5. Once connected, observe the **Google Services Integration** card showing:
   - Google Account email
   - Active status
   - Selected GA4 Property and Search Console site
   - Last successful sync timestamps
6. Click **Configure Properties**.
7. In the dropdown, select the specific GA4 Property and Search Console site owned by this organization.
8. Click **Save Properties**. Verify configuration is saved per-organization and per-website.
9. Click **Sync Now** to fetch real metrics into the dashboard.

### Scenario 7: AI Growth Advisor & Marketing Recommendations
1. Navigate to `/workspace/ai`.
2. Verify the AI advisor dashboard loads with category filters (`SEO`, `CONTENT`, `CONVERSION`, `MARKETING`).
3. Review recommended growth actions derived from website audits and CRM trends.
4. Test AI prompt input: verify prompt injection attempts (e.g., "Ignore all previous instructions") are handled safely.
5. Test offline fallback: when OpenAI API key is unavailable, verify the application displays deterministic rule-based growth recommendations rather than crashing.

### Scenario 8: Chapa Subscription Billing & Webhook Activation
1. Navigate to `/workspace/billing`.
2. Verify current subscription status (default: `FREE` tier).
3. Click **Upgrade to Pro** (or Enterprise).
4. Verify redirection to Chapa checkout page with transaction reference `YBG-...`.
5. In test environment, verify that webhook callbacks to `/api/v1/billing/webhook/chapa` require:
   - Valid `x-chapa-signature` matching HMAC-SHA256 of the payload.
   - Server-side verification via `https://api.chapa.co/v1/transaction/verify/{txRef}` before activating subscription.
6. Verify subscription tier upgrades to `PRO` upon verified payment.

### Scenario 9: Email Verification & Security Reset Flows
1. Register an account and check for email verification token.
2. Visit `/verify-email?token=...` and verify email confirmation.
3. Test `/forgot-password`: submit registered email. Verify generic success message is displayed without leaking whether the account exists.
4. Use password reset token to set a new password on `/reset-password?token=...`.
5. Verify old password no longer authenticates.

### Scenario 10: Responsive Layout & Mobile Usability
1. In Google Chrome, press `F12` to open DevTools and toggle Device Toolbar (`Ctrl+Shift+M`).
2. Test responsive layouts on **iPhone 14** (390x844) and **iPad Air** (820x1180).
3. Verify mobile navigation drawer toggles cleanly without content layout shift.
4. Verify table horizontal scrolling on `/workspace/leads` and `/workspace/websites` displays cleanly on mobile viewports.
