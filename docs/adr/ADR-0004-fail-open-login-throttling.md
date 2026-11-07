# ADR-0004: Fail-open Redis login throttling

- **Status:** Accepted (2026-11)
- **Context:** Credential-stuffing defense needs a counter per e-mail
  (10 attempts / 15 min). The counter lives in Redis — an external
  dependency. If Redis is down, the choice is: fail closed (nobody can
  log in, including legitimate users) or fail open (throttling is skipped,
  availability preserved).
- **Decision:** **Fail open.** `LoginThrottleService` catches Redis
  errors, logs a warning, and lets authentication proceed. Counting
  happens before credential verification; a successful login clears the
  window; the limit returns 429 with the standard envelope.
- **Consequences:**
  - ✅ A Redis outage degrades to "no throttle" instead of "no logins" —
    an outage of the cache must not lock every customer out of the
    product.
  - ❌ During a Redis outage, brute-force protection is absent — accepted
    for a single-backend first release; compensating controls: BCrypt
    (expensive to verify), generic error messages (no enumeration), short
    JWT TTLs.
  - Revisit before scale: circuit breaker + local in-memory fallback
    bucket (per-JVM approximations) rather than pure pass-through.
  - The behavior is pinned by `LoginThrottleServiceTest` with a mocked
    failing Redis connection.
