# YemomBizGrow — Production Readiness Audit & Remediation Report

**Repository:** https://github.com/yemom/yemomTech-sas  
**Base Commit Evaluated:** `91069e3450d11ba78a30a19a07d48f79a5407aab`  
**Current Branch:** `main`  
**Audit & Remediation Date:** 2026-11-11  

---

## 1. Executive Summary & Verification Matrix

| Finding ID | Assessment Area | Severity | Baseline Status | Post-Remediation Status | Verification Method & Results |
|---|---|---|---|---|---|
| **AUD-01** | Package & Directory Tree Alignment | **P0** | Broken | **RESOLVED** | All 238 source files unified under `com/YemomBizGrow/`. `.\mvnw.cmd clean test` passes (233 tests). |
| **AUD-02** | Frontend Dependency Mismatch | **P1** | Broken | **RESOLVED** | Replaced typo `@testing-library/user-browser` with `@testing-library/user-event`. `npm ci`, typecheck, lint, test (21 tests), and build (23 routes) all pass. |
| **AUD-03** | Chapa Payment Verification & Lock Asymmetry | **P0** | Vulnerable | **RESOLVED** | Extracted and validated provider `tx_ref` & `reference` from Chapa payload; implemented pessimistic row locking in `verifyPayment()`; added `ChapaClientTest`. |
| **AUD-04** | Unique Subscription Constraint | **P1** | At Risk | **RESOLVED** | Created Flyway migration `V18__enforce_unique_org_subscription.sql` enforcing unique active subscription per organization. |
| **AUD-05** | SafeHttpClient Unbounded Header & Slow-Drip DoS | **P1** | Vulnerable | **RESOLVED** | Added `MAX_LINE_BYTES` (8KB) bound in `readLine()`, `MAX_HEADER_COUNT` enforcement, and request deadline checks in `readBodyWithLimit` and `readChunkedBody`. |
| **AUD-06** | Google OAuth Refresh Token Fail-Closed | **P1** | Defective | **RESOLVED** | When token refresh fails or refresh token is missing, `getValidAccessToken()` throws `BadRequestException` and fails closed. Verified by unit test. |
| **AUD-07** | Public Lead Capture Fail-Open Rate Limiter | **P1** | Vulnerable | **RESOLVED** | Implemented `ConcurrentHashMap` in-memory fallback rate limiter with sliding window eviction when Redis is unavailable. Verified by unit test. |
| **AUD-08** | AI Public Chatbot Rate Limit & Bounded History | **P2** | Inconsistent | **RESOLVED** | Enforced 5 msg/min rate limit; added DB-bounded `findTop10ByConversationIdOrderByCreatedAtDesc`; wired client IP in `PublicChatbotController`. Verified by unit test. |
| **AUD-09** | Playwright Multi-Browser Configuration | **P2** | Misconfigured | **RESOLVED** | Scoped `channel: "chrome"` specifically to `chrome` project; bundled Chromium used in `chromium` project. Verified via `npx playwright test --list`. |

---

## 2. Detailed Findings Register & Remediation Log

### Finding AUD-01: Split-Brain Package Directory Mismatch (P0)
- **Module/Files:** `backend/src/main/java/com/bizgrow`, `backend/src/test/java/com/bizgrow`, `backend/src/main/java/com/YemomBizGrow`, `backend/src/test/java/com/YemomBizGrow`
- **Severity:** P0 (Critical - Build Breaker)
- **Root Cause:** A previous refactoring renamed Java package statements to `package com.YemomBizGrow.*` but left the underlying directory structure as `com/bizgrow/`. Subsequently, five files were moved to `com/YemomBizGrow/`, leaving the codebase split across two directories. On clean compilation, javac threw `NoClassDefFoundError` and `NoSuchFileException`.
- **Correction Applied:** Unified the entire source tree so that all source and test files reside under `backend/src/main/java/com/YemomBizGrow/` and `backend/src/test/java/com/YemomBizGrow/`. Removed stale split directories and cleaned leftover imports.
- **Verification Method:** `cd backend; .\mvnw.cmd clean test-compile` and `.\mvnw.cmd test`.
- **Status:** **RESOLVED** (233 unit tests pass cleanly).

---

### Finding AUD-02: Frontend Dependency Mismatch `@testing-library/user-browser` (P1)
- **Module/Files:** `frontend/package.json`, `frontend/package-lock.json`
- **Severity:** P1 (High)
- **Root Cause:** Typo in `package.json` referencing `@testing-library/user-browser` while lockfile and actual testing ecosystem use `@testing-library/user-event`.
- **Correction Applied:** Replaced `@testing-library/user-browser` with `@testing-library/user-event` in `frontend/package.json` and verified lockfile consistency.
- **Verification Method:** `cd frontend; npm ci; npm run typecheck; npm run test; npm run build`.
- **Status:** **RESOLVED** (npm ci clean, 21 vitest tests pass, next build succeeds with 23 routes).

---

### Finding AUD-03: Chapa Verification Client Does Not Validate Provider's `tx_ref` & Concurrency Lock Asymmetry (P0)
- **Module/Files:** `backend/src/main/java/com/YemomBizGrow/billing/ChapaClient.java`, `BillingService.java`, `PaymentTransactionRepository.java`
- **Severity:** P0 (Financial Security)
- **Root Cause:**
  1. `ChapaClient.verify(txRef)` ignored `data.path("tx_ref")` from Chapa's response and passed caller's `txRef` as the verified reference. Also defaulted Chapa's transaction ID to `txRef` if missing.
  2. `verifyPayment()` used non-locking `findByTxRefAndOrganizationId()` while `handleWebhook()` used `findByTxRefForUpdate()`, opening a race condition during concurrent user verification and webhook arrival.
- **Correction Applied:**
  1. Updated `ChapaClient.verify()` to parse `data.path("tx_ref")` and `data.path("reference")`. Fails closed if provider `tx_ref` or `reference` is missing or mismatched.
  2. Added `findByTxRefAndOrganizationIdForUpdate()` to `PaymentTransactionRepository` and integrated into `verifyPayment()`.
  3. Added provider transaction ID verification in both webhook and interactive payment verification paths.
  4. Added `ChapaClientTest.java` verifying HMAC-SHA256 signature validation and live configuration checks.
- **Verification Method:** Unit tests in `BillingServiceTest` and `ChapaClientTest`.
- **Status:** **RESOLVED**.

---

### Finding AUD-04: Lack of Unique Constraint on Organization Subscriptions (P1)
- **Module/Files:** `backend/src/main/resources/db/migration/V18__enforce_unique_org_subscription.sql`
- **Severity:** P1 (Data Integrity)
- **Root Cause:** `organization_subscriptions` table in V14 lacked a unique constraint on `organization_id`, allowing potential duplicate active subscriptions under race conditions.
- **Correction Applied:** Created forward Flyway migration `V18__enforce_unique_org_subscription.sql` that deduplicates existing rows (retaining the latest updated) and creates a unique index `idx_org_subscriptions_org_id_unique ON organization_subscriptions(organization_id)`.
- **Verification Method:** Migration syntax and clean build validation.
- **Status:** **RESOLVED**.

---

### Finding AUD-05: SafeHttpClient Unbounded Parsing and Slow-Drip Vulnerability (P1)
- **Module/Files:** `backend/src/main/java/com/YemomBizGrow/seo/crawler/SafeHttpClient.java`
- **Severity:** P1 (Network / SSRF Hardening)
- **Root Cause:**
  1. `readLine(InputStream in)` had no line-length limit, permitting unbounded memory allocation on infinite status/header lines.
  2. Response header count was not enforced in `readHeadersWithSizeLimit()`.
  3. Body reading loops (`readBodyWithLimit` and `readChunkedBody`) did not check the 30-second absolute deadline during read iterations, allowing slow-drip responses to tie up connections.
- **Correction Applied:** Enforced `MAX_LINE_BYTES = 8192` in `readLine`, enforced `headers.size() >= MAX_HEADER_COUNT` break in `readHeadersWithSizeLimit`, and added `Instant.now().isAfter(deadline)` checks inside both fixed and chunked body read loops.
- **Verification Method:** Executed `SafeHttpClientTest` and `SsrfProtectionServiceTest`.
- **Status:** **RESOLVED**.

---

### Finding AUD-06: Google OAuth Expired Token Returned on Refresh Failure (P1)
- **Module/Files:** `backend/src/main/java/com/YemomBizGrow/integration/google/GoogleIntegrationService.java`, `GoogleIntegrationServiceTest.java`
- **Severity:** P1 (Integration Security)
- **Root Cause:** When `oAuthClient.refreshAccessToken()` threw an exception, `getValidAccessToken()` logged the error and returned the original expired token.
- **Correction Applied:** If access token is expired and refresh fails (or refresh token is missing), throw `BadRequestException("Google integration requires reconnection: access token expired and refresh failed. Please reconnect your Google account.")`.
- **Verification Method:** Added unit test `syncAnalyticsData_whenTokenExpiredAndRefreshFails_throwsBadRequestException()` in `GoogleIntegrationServiceTest`.
- **Status:** **RESOLVED**.

---

### Finding AUD-07: Public Lead Capture Rate Limiting Fails Open (P1)
- **Module/Files:** `backend/src/main/java/com/YemomBizGrow/crm/LeadService.java`, `LeadServiceTest.java`
- **Severity:** P1 (Abuse Prevention)
- **Root Cause:** `enforceLeadRateLimit(clientIp)` caught all Redis exceptions and logged a warning without enforcing any limit, allowing bot floods if Redis was down or unavailable.
- **Correction Applied:** Implemented a thread-safe `ConcurrentHashMap` in-memory fallback rate limiter with timestamp tracking and window eviction (10 requests per 15 minutes). When Redis throws an exception or is null, fallback rate limiting is enforced.
- **Verification Method:** Added unit test `capturePublicLead_fallbackToInMemoryRateLimit()` in `LeadServiceTest`.
- **Status:** **RESOLVED**.

---

### Finding AUD-08: AI Public Chatbot Inconsistent Limit & Unbounded History Query (P2)
- **Module/Files:** `backend/src/main/java/com/YemomBizGrow/ai/AiService.java`, `ChatbotMessageRepository.java`, `PublicChatbotController.java`, `AiServiceTest.java`
- **Severity:** P2 (Performance / AI Safety)
- **Root Cause:**
  1. `AiService` fetched the entire conversation history via `findByConversationIdOrderByCreatedAtAsc` before slicing the last 10 messages in memory.
  2. Rate limit comment stated 5 msg/min, but code checked `>= 50`.
  3. Client IP was not extracted in `PublicChatbotController`.
- **Correction Applied:**
  1. Added `findTop10ByConversationIdOrderByCreatedAtDesc` in `ChatbotMessageRepository` for bounded DB queries.
  2. Aligned rate limit check to `>= 5` messages per minute.
  3. Wired `ClientIpResolver` into `PublicChatbotController` and passed client IP to `AiService.handlePublicChat()`.
- **Verification Method:** Added unit test `handlePublicChat_whenRateLimitExceeded_throwsBadRequest()` in `AiServiceTest`.
- **Status:** **RESOLVED**.

---

### Finding AUD-09: Playwright Multi-Browser Configuration (P2)
- **Module/Files:** `frontend/playwright.config.ts`
- **Severity:** P2 (QA Automation)
- **Root Cause:** Global `use: { channel: "chrome" }` overrode the bundled Chromium project, preventing true standalone Chromium headless execution in CI environments without Google Chrome.
- **Correction Applied:** Removed `channel: "chrome"` from the top-level `use` block and scoped it specifically to the `chrome` project. The `chromium` project now utilizes bundled Chromium.
- **Verification Method:** Verified project list and configuration with `npx playwright test --list` (20 tests across chrome and chromium projects).
- **Status:** **RESOLVED**.
