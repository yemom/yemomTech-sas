# ADR-0003: Refresh-token rotation with family revocation

- **Status:** Accepted (2026-11)
- **Context:** Access tokens must be short-lived (15 min) for damage
  containment, which requires a renewal mechanism. Long-lived static
  refresh tokens are a standing prize for thieves; the classic mitigations
  are rotation-on-use and reuse detection. Mobile networks and multiple
  tabs also make legitimate concurrent refreshes likely.
- **Decision:**
  - Refresh tokens are opaque 256-bit random values, stored **only as
    SHA-256 digests**, carried in an `HttpOnly` cookie, and **rotated on
    every refresh** (old row revoked, `replaced_by` chain).
  - Every login creates a **family** (`family_id`); presenting an
    already-rotated token is **reuse** → the entire family is revoked and
    the request fails 401 (both parties get logged out — the safe default
    when you cannot tell attacker from victim).
  - Frontend: refresh is **single-flight per tab** and coordinated across
    tabs via the **Web Locks API**, so two tabs never rotate concurrently
    and trip the reuse detector.
  - Logout revokes the family and clears cookies (idempotent 204).
- **Consequences:**
  - ✅ Stolen cookie is single-use; replay kills the family and alerts the
    legitimate session.
  - ✅ SHA-256 storage means a DB dump cannot be replayed.
  - ❌ Legit-but-racy refreshes (e.g. a stale tab after laptop sleep)
    log the user out — mitigated by cross-tab locking; cost accepted.
  - ❌ 15-minute access tokens stay valid after logout until expiry —
    accepted (short TTL); a denylist would add state to every request.
  - Covered by `VerticalSliceIT` (rotation + replay → family revoked).
