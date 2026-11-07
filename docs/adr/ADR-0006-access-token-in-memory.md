# ADR-0006: Access token in memory, refresh in HttpOnly cookie

- **Status:** Accepted (2026-11)
- **Context:** Storing JWTs in `localStorage` makes them readable by any
  injected script (XSS ⇒ full account takeover until expiry, plus the
  token travels in every request header). Cookies are automatically sent
  but need CSRF protection. Fully HttpOnly-everything would break the
  "send as Bearer" ergonomics and proactive refresh scheduling.
- **Decision:**
  - **Access token: module-level memory only** in the API client — never
    `localStorage`/`sessionStorage`/cookies. It dies on page reload and is
    re-obtained via one refresh call during bootstrap.
  - **Refresh token: `HttpOnly` cookie** (not readable by JS at all),
    rotated on use (ADR-0003).
  - **CSRF: double-submit** — a non-HttpOnly `csrf` cookie echoed in the
    `X-CSRF-TOKEN` header on the two cookie-authenticated endpoints
    (refresh, logout); the frontend mirrors the value to
    `localStorage.bizgrow_csrf` purely to echo it (useless without the
    HttpOnly cookie).
  - On 401: one refresh (single-flight + Web Locks) → one retry; a
    terminal failure raises an `ended` session event and the app drops to
    anonymous.
- **Consequences:**
  - ✅ XSS cannot exfiltrate either token (access: not in any storage;
    refresh: HttpOnly).
  - ✅ Reloads are seamless (silent refresh on bootstrap) with no token
    persisted anywhere.
  - ❌ Every fresh page load costs one refresh round-trip (accepted;
    measured in the tens of milliseconds on localhost).
  - ❌ Cross-tab coordination needs Web Locks; multi-window edge cases are
    covered by family revocation being the safety net.
  - Verified manually in-browser: login → reload → logout → `next`
    roundtrip all behave; covered by E2E tests.
