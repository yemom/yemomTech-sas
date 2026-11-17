#!/usr/bin/env bash
# =============================================================================
# Staging authentication audit — runs the full session lifecycle against a
# DEPLOYED backend with curl (raw HTTP, no client-library shortcuts).
#
#   bash infrastructure/scripts/staging-auth-audit.sh [BASE_URL]
#
# BASE_URL default: http://localhost:8082/api/v1  (compose backend on this
# machine; production would be https://host/api/v1). Registers a throwaway
# account; performs no destructive operation.
# =============================================================================
set -u

BASE="${1:-http://localhost:8082/api/v1}"
ORIGIN="${BASE%/api/v1}"
PASSWORD="Correct-Horse-9!"
EMAIL="audit-$(date +%s)@example.et"
JAR="$(mktemp)"

# Extra curl options, e.g. a CA bundle for an HTTPS rehearsal with an
# internal (self-signed) CA:
#   AUDIT_CURL_EXTRA="--cacert staging-root.crt" bash staging-auth-audit.sh https://localhost:8443/api/v1
CURL_EXTRA=()
if [[ -n "${AUDIT_CURL_EXTRA:-}" ]]; then
  read -ra CURL_EXTRA <<< "$AUDIT_CURL_EXTRA"
fi
# Wrap curl so every call below picks the extra options up; `command curl`
# reaches the real binary (this function does not recurse).
curl() { command curl "${CURL_EXTRA[@]}" "$@"; }

pass=0; fail=0
check() { # check NAME EXPECTED ACTUAL
  if [[ "$2" == "$3" ]]; then
    printf '%-58s PASS  %s\n' "$1" "$4"; pass=$((pass+1))
  else
    printf '%-58s FAIL  expected=%s got=%s (%s)\n' "$1" "$2" "$3" "$4"; fail=$((fail+1))
  fi
}

command -v curl >/dev/null || { echo "curl is required"; exit 1; }

# ---- 1. registration issues the session and correct cookies ----------------
REGISTER_HEADERS="$(mktemp)"
status=$(curl -s -o "$JAR-body" -D "$REGISTER_HEADERS" -w '%{http_code}' \
  -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Audit\",\"lastName\":\"Probe\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  -c "$JAR")
check "register returns 201" "201" "$status" "POST /auth/register"
TOKEN="$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' "$JAR-body")"
check "register returns an access token" "set" "$([[ -n "$TOKEN" ]] && echo set || echo missing)" "body parse"

REFRESH_FLAGS="$(grep -i 'Set-Cookie: YemomBizGrow_refresh' "$REGISTER_HEADERS" || true)"
echo "$REFRESH_FLAGS" | grep -qi "HttpOnly" \
  && check "refresh cookie is HttpOnly" "1" "1" "" \
  || check "refresh cookie is HttpOnly" "1" "0" "$REFRESH_FLAGS"
echo "$REFRESH_FLAGS" | grep -qi "Path=/api/v1/auth" \
  && check "refresh cookie path is /api/v1/auth" "1" "1" "" \
  || check "refresh cookie path is /api/v1/auth" "1" "0" "$REFRESH_FLAGS"
echo "$REFRESH_FLAGS" | grep -qi "SameSite=" \
  && check "refresh cookie declares SameSite" "1" "1" "$(echo "$REFRESH_FLAGS" | grep -oi 'SameSite=[A-Za-z]*')" \
  || check "refresh cookie declares SameSite" "1" "0" "$REFRESH_FLAGS"
grep -qi "YemomBizGrow_csrf" "$JAR" \
  && check "csrf cookie issued (readable by design)" "1" "1" "" \
  || check "csrf cookie issued" "1" "0" "missing"

CSRF="$(grep -o 'YemomBizGrow_csrf[[:space:]].*$' "$JAR" | awk '{print $NF}')"

# ---- 2. /auth/me with the bearer token ------------------------------------
status=$(curl -s -o "$JAR-me" -w '%{http_code}' "$BASE/auth/me" -H "Authorization: Bearer $TOKEN")
email_ok=$(grep -o "\"email\":\"$EMAIL\"" "$JAR-me" | head -1)
check "/auth/me returns the profile (200)" "200" "$status" "GET /auth/me"
check "/auth/me returns the registered user" "1" "$([[ -n "$email_ok" ]] && echo 1 || echo 0)" "$email_ok"

# ---- 3. /auth/me without a token ------------------------------------------
status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/auth/me")
check "/auth/me without a token is 401" "401" "$status" ""

# ---- 4. refresh without the CSRF header ------------------------------------
status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/refresh" -b "$JAR")
check "refresh without CSRF header is 403" "403" "$status" "double-submit enforced"

# ---- 5. refresh with the CSRF header rotates everything --------------------
OLD_REFRESH_VALUE="$(grep 'YemomBizGrow_refresh' "$JAR" | awk '{print $NF}')"
status=$(curl -s -o "$JAR-refresh" -D "$JAR-refresh-headers" -w '%{http_code}' \
  -X POST "$BASE/auth/refresh" -b "$JAR" -c "$JAR" -H "X-CSRF-TOKEN: $CSRF")
check "refresh with CSRF header is 200" "200" "$status" ""
NEW_TOKEN="$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' "$JAR-refresh")"
[[ -n "$NEW_TOKEN" && "$NEW_TOKEN" != "$TOKEN" ]] \
  && check "refresh rotates the access token" "1" "1" "" \
  || check "refresh rotates the access token" "1" "0" "same or missing"
NEW_REFRESH_VALUE="$(grep 'YemomBizGrow_refresh' "$JAR" | awk '{print $NF}')"
[[ -n "$NEW_REFRESH_VALUE" && "$NEW_REFRESH_VALUE" != "$OLD_REFRESH_VALUE" ]] \
  && check "refresh rotates the cookie value" "1" "1" "" \
  || check "refresh rotates the cookie value" "1" "0" "same or missing"
TOKEN="$NEW_TOKEN"; CSRF="$(grep -o 'YemomBizGrow_csrf[[:space:]].*$' "$JAR" | awk '{print $NF}')"

# ---- 6. RBAC: a foreign organization is denied ------------------------------
status=$(curl -s -o /dev/null -w '%{http_code}' \
  "$BASE/organizations/$(uuidgen 2>/dev/null || echo 00000000-0000-0000-0000-000000000000)/reports/comprehensive" \
  -H "Authorization: Bearer $TOKEN")
case "$status" in 403|404) check "foreign organization report denied" "1" "1" "HTTP $status";;
                 *) check "foreign organization report denied" "1" "0" "HTTP $status";; esac

# ---- 7. CSV export: authenticated 200, unauthenticated 401 ------------------
status=$(curl -s -o "$JAR-org" -w '%{http_code}' -X POST "$BASE/organizations" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Audit Org $(date +%s)\"}")
check "create workspace" "201" "$status" "POST /organizations"
ORG_ID="$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$JAR-org")"
csv_headers="$(mktemp)"
status=$(curl -s -o "$JAR-csv" -D "$csv_headers" -w '%{http_code}' \
  "$BASE/organizations/$ORG_ID/reports/export/csv" -H "Authorization: Bearer $TOKEN")
ctype="$(grep -i '^Content-Type' "$csv_headers" | tr -d '\r')"
check "CSV export with bearer token" "200" "$status" "$ctype"
[[ "$(wc -c < "$JAR-csv")" -gt 0 ]] \
  && check "CSV export returns a non-empty body" "1" "1" "$(wc -c < "$JAR-csv") bytes" \
  || check "CSV export returns a non-empty body" "1" "0" "empty"
status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/organizations/$ORG_ID/reports/export/csv")
check "CSV export without token is 401" "401" "$status" ""

# ---- 8. logout invalidates both the token and the refresh cookie ------------
status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/logout" \
  -b "$JAR" -H "X-CSRF-TOKEN: $CSRF" -H "Authorization: Bearer $TOKEN")
check "logout is 2xx" "1" "$([[ $status -ge 200 && $status -lt 300 ]] && echo 1 || echo 0)" "HTTP $status"
status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/auth/me" -H "Authorization: Bearer $TOKEN")
# Documented tradeoff (AUTHENTICATION_AND_SECURITY.md, limitation 4): the
# stateless access token remains valid up to its 15-minute TTL after
# logout; the refresh family is revoked, which is what bounds the exposure.
check "access token bounded by 15-min TTL after logout (documented)" "200" "$status" ""
status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/refresh" \
  -b "$JAR" -H "X-CSRF-TOKEN: $CSRF")
case "$status" in 401|403) check "refresh rejected after logout" "1" "1" "HTTP $status";;
                  *) check "refresh rejected after logout" "1" "0" "HTTP $status";; esac

# ---- 9. login with the registered credentials -------------------------------
status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "login with the registered credentials" "200" "$status" ""

rm -f "$JAR" "$JAR-body" "$JAR-me" "$JAR-refresh" "$JAR-refresh-headers" "$JAR-org" "$JAR-csv" "$csv_headers" "$REGISTER_HEADERS"
echo
echo "SUMMARY: $((pass+fail)) checks, $pass passed, $fail failed"
[[ "$fail" -eq 0 ]]
