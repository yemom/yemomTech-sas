# ADR-0005: No outbound URL fetching (no SSRF surface)

- **Status:** Accepted (2026-11)
- **Context:** The product registers business website URLs. The natural
  temptation is to fetch them at registration (verify liveness, scrape a
  title/favicon, preview content). Any server-side fetch of a user-supplied
  URL is an **SSRF** vector: internal metadata endpoints
  (`169.254.169.254`), localhost admin panels, port scanning, DNS
  rebinding. The first slice does not need fetched data — registration is
  metadata-only.
- **Decision:** **The backend never makes outbound requests to
  user-supplied URLs.** `WebsiteUrlNormalizer` validates and normalizes
  (http/https only, public domains only — no localhost, no IP literals, no
  embedded credentials, host lowercased) and the result is stored; nothing
  is fetched. This is a deliberate architectural invariant, documented in
  `WebsiteService`'s javadoc and the API spec.
- **Consequences:**
  - ✅ The SSRF class is structurally absent, not just filtered — there is
    no HTTP client to exploit.
  - ✅ Registration is fast and deterministic; no flaky third-party
    dependency in the critical path.
  - ❌ No liveness/ownership proof of a registered domain (accepted: the
    value today is bookkeeping, not verification).
  - When a crawler/auditor is added (SEO module), it must re-run the
    normalizer **plus**: resolve DNS and reject private/loopback/link-local
    ranges *at connect time* (re-check after resolution to beat DNS
    rebinding), allowlist ports (80/443), cap response size and time, and
    run in a network-isolated worker. This is a prerequisite on the
    ROADMAP, not an afterthought.
