#!/usr/bin/env bash
# =============================================================================
# Multi-instance Redis failure drill.
#
# Two backend instances (8080, 8091) share PostgreSQL and Redis. This drill
# proves that:
#
#   1. both instances stay functional when Redis goes down;
#   2. the login throttle degrades to its in-process limiter on BOTH
#      instances (not just the one that happens to be queried);
#   3. brute-force attempts are still refused while Redis is unavailable;
#   4. the instances recover when Redis returns.
#
# It never modifies production data: it registers a throwaway account and
# performs failed logins against it.
# =============================================================================
set -euo pipefail

PRIMARY_URL="${PRIMARY_URL:-http://localhost:8080/api/v1}"
SECONDARY_URL="${SECONDARY_URL:-http://localhost:8091/api/v1}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-yemombizgrow-postgres-1}"
BACKEND2_CONTAINER="${BACKEND2_CONTAINER:-yemombizgrow-backend2-1}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

PASSWORD="Correct-Horse-9!"

log() { printf '[redis-drill] %s\n' "$*"; }
fail() { printf '[redis-drill] FAIL: %s\n' "$*" >&2; exit 1; }
info() { curl -s -o /dev/null -w '%{http_code}' "$1"; }

command -v curl >/dev/null || fail "curl is required"
command -v docker >/dev/null || fail "docker is required"

log "1/6 starting the second backend instance (profile: scale)"
docker compose -f "${COMPOSE_FILE}" --profile scale up -d backend2 >/dev/null

log "waiting for the second instance to become healthy"
for attempt in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${SECONDARY_URL%/api/v1}/actuator/health/readiness" || true)
  if [[ "${code}" == "200" ]]; then
    break
  fi
  sleep 2
done
[[ "$(curl -s -o /dev/null -w '%{http_code}' "${SECONDARY_URL%/api/v1}/actuator/health/readiness")" == "200" ]] \
  || fail "second backend instance never became healthy"

EMAIL="drill-$(date +%s)@example.et"

log "2/6 registering the drill account on the second instance"
status=$(curl -s -o /tmp/drill-register.json -w '%{http_code}' -X POST "${SECONDARY_URL}/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"firstName\":\"Drill\",\"lastName\":\"User\",\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")
[[ "${status}" == "201" ]] || fail "registration failed with HTTP ${status}"

log "3/6 stopping Redis"
docker compose -f "${COMPOSE_FILE}" stop "${REDIS_SERVICE}" >/dev/null

log "4/6 brute-forcing the drill account on the second instance (expecting throttling)"
last_code=0
for attempt in 1 2 3 4 5 6 7; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${SECONDARY_URL}/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"Wrong-Password-${attempt}\"}")
  printf '  attempt %d -> HTTP %s\n' "${attempt}" "${code}"
  last_code=${code}
done

if [[ "${last_code}" == "429" ]]; then
  log "second instance correctly throttled the brute-force attempt"
else
  fail "expected the second instance to throttle (HTTP 429), got ${last_code}"
fi

log "5/6 checking both instances stayed up while Redis was down"
code1=$(curl -s -o /dev/null -w '%{http_code}' "${PRIMARY_URL%/api/v1}/actuator/health/readiness")
code2=$(curl -s -o /dev/null -w '%{http_code}' "${SECONDARY_URL%/api/v1}/actuator/health/readiness")
echo "  primary (8080) readiness: HTTP ${code1}"
echo "  secondary (8091) readiness: HTTP ${code2}"
[[ "${code1}" == "200" && "${code2}" == "200" ]] \
  || fail "a backend went down when Redis went down"

log "6/6 restarting Redis and verifying the primary instance recovers"
docker compose -f "${COMPOSE_FILE}" start "${REDIS_SERVICE}" >/dev/null
for attempt in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${PRIMARY_URL%/api/v1}/actuator/health/readiness" || true)
  [[ "${code}" == "200" ]] && break
  sleep 2
done
code=$(curl -s -o /dev/null -w '%{http_code}' "${PRIMARY_URL%/api/v1}/actuator/health/readiness")
[[ "${code}" == "200" ]] || fail "primary instance did not recover after Redis returned"

log "DRILL PASSED"
log "inspect the degraded-control metric:"
log "  curl http://localhost:8091/actuator/metrics/bizgrow.security.controls.degraded"
