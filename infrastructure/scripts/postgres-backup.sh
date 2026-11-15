#!/usr/bin/env bash
# =============================================================================
# YemomBizGrow — PostgreSQL backup, restore and verification.
#
#   ./postgres-backup.sh backup   [database]   -> writes a compressed dump
#   ./postgres-backup.sh restore  <file> <target-db>
#   ./postgres-backup.sh verify   [database]   -> backup + restore into a
#                                               scratch database and compare
#
# Safety rules enforced by this script:
#   * the restore target may never be the database the dump came from;
#   * the scratch database used by "verify" is dropped again afterwards;
#   * nothing here ever touches a production database unless the operator
#     explicitly points it at one (BACKUP_CONTAINER / DATABASE_URL).
#
# Usage with the compose stack:
#   ./postgres-backup.sh backup
#   ./postgres-backup.sh verify
# =============================================================================
set -euo pipefail

# Git Bash / MSYS rewrites any POSIX-looking argument into a Windows path
# before it reaches a native binary. That corrupts container-side paths such
# as /tmp/backup.dump. Path conversion is therefore disabled globally and
# host paths are converted explicitly with cygpath (see host_path_for_docker).
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Container that holds PostgreSQL. Override for a remote/managed database.
: "${PG_CONTAINER:=yemombizgrow-postgres-1}"
: "${PG_USER:=YemomBizGrow_user}"
: "${PG_HOST:=localhost}"
: "${PG_PORT:=5635}"
: "${BACKUP_DIR:=${REPO_ROOT}/backups}"
: "${SOURCE_DB:=YemomBizGrow}"

# The scratch database used by "verify" — never a real one.
: "${SCRATCH_DB:=yemombizgrow_backup_verify}"
# Where the archive is staged inside the container (pg_restore needs to seek).
: "${CONTAINER_ARCHIVE:=/tmp/yemombizgrow_backup.dump}"

log() { printf '[postgres-backup] %s\n' "$*" >&2; }
fail() { printf '[postgres-backup] ERROR: %s\n' "$*" >&2; exit 1; }

require_container() {
  if ! docker ps --format '{{.Names}}' | grep -qx "${PG_CONTAINER}"; then
    fail "PostgreSQL container '${PG_CONTAINER}' is not running. Start it with: docker compose up -d postgres"
  fi
}

# Run psql inside the container.
psql_exec() {
  docker exec -i "${PG_CONTAINER}" psql -U "${PG_USER}" -d "$1" -v ON_ERROR_STOP=1 -tAc "$2"
}

# Run psql against a database on the host (used for the restored scratch db).
psql_host() {
  docker exec -i "${PG_CONTAINER}" psql -U "${PG_USER}" -d "$1" -v ON_ERROR_STOP=1 -tAc "$2"
}

# Git Bash / MSYS rewrites POSIX-looking arguments into Windows paths before
# they reach a native binary, which corrupts container-side paths such as
# /tmp/backup.dump. Convert the host path explicitly and disable the
# automatic conversion for the container path.
host_path_for_docker() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

# Copy a dump into the container (pg_restore has to seek inside the archive,
# which is impossible when it is piped through stdin).
copy_into_container() {
  docker cp "$(host_path_for_docker "$1")" "${PG_CONTAINER}:${CONTAINER_ARCHIVE}" >/dev/null
}

do_backup() {
  local db="${1:-${SOURCE_DB}}"
  require_container
  mkdir -p "${BACKUP_DIR}"
  local stamp file
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  file="${BACKUP_DIR}/${db}-${stamp}.dump"

  log "dumping database '${db}' -> ${file}"
  docker exec "${PG_CONTAINER}" pg_dump -U "${PG_USER}" -d "${db}" --format=custom --no-owner --no-privileges \
    > "${file}"

  [[ -s "${file}" ]] || fail "the dump is empty"
  log "dump written: $(du -h "${file}" | cut -f1)"

  # Integrity: the archive must be readable by pg_restore and list its tables.
  # The archive is copied into the container first - pg_restore has to seek
  # inside it, which is not possible when it is piped through stdin.
  copy_into_container "${file}"
  local table_count
  table_count="$(docker exec "${PG_CONTAINER}" pg_restore --list "${CONTAINER_ARCHIVE}" 2>/dev/null | grep -c 'TABLE DATA' || true)"
  [[ "${table_count}" -gt 0 ]] || fail "the dump contains no table data"
  log "dump verified: ${table_count} tables present"
  printf '%s\n' "${file}"
}

# The safety rule, isolated so it can be tested without touching a database.
# Returns 0 when the restore is allowed, 1 when it must be refused.
is_safe_restore_target() {
  local source="$1" target="$2"
  [[ "${target}" != "${source}" ]]
}

do_restore() {
  local file="${1:?usage: restore <file> <target-db>}"
  local target="${2:?usage: restore <file> <target-db>}"
  require_container

  [[ -f "${file}" ]] || fail "no such dump: ${file}"
  if ! is_safe_restore_target "${SOURCE_DB}" "${target}"; then
    fail "refusing to restore over the source database '${target}'"
  fi

  log "restoring ${file} into '${target}'"
  copy_into_container "${file}"
  docker exec -i "${PG_CONTAINER}" psql -U "${PG_USER}" -d postgres -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS \"${target}\" WITH (FORCE);" >/dev/null
  docker exec -i "${PG_CONTAINER}" psql -U "${PG_USER}" -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE \"${target}\";" >/dev/null
  docker exec "${PG_CONTAINER}" pg_restore -U "${PG_USER}" -d "${target}" --no-owner --no-privileges \
    "${CONTAINER_ARCHIVE}"
  docker exec "${PG_CONTAINER}" rm -f "${CONTAINER_ARCHIVE}"
  log "restore complete"
}

# Row counts of every table, as a stable, comparable signature.
table_signature() {
  local db="$1"
  local tables
  tables="$(psql_host "${db}" "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;")"
  local out=""
  for t in ${tables}; do
    local count
    count="$(psql_host "${db}" "SELECT count(*) FROM \"${t}\";")"
    out+="${t}=${count}"$'\n'
  done
  printf '%s' "${out}"
}

do_verify() {
  local db="${1:-${SOURCE_DB}}"
  require_container

  log "=== 1/5 checking the safety guard ==="
  # The script must refuse to restore a dump over the database it came from.
  # The rule is checked in isolation — no database is touched by this step.
  if is_safe_restore_target "${db}" "${db}"; then
    fail "the restore guard did not protect the source database"
  fi
  log "restore over the source database is refused"

  log "=== 2/5 creating a backup of '${db}' ==="
  local dump
  dump="$(do_backup "${db}")"

  log "=== 3/5 restoring it into the isolated database '${SCRATCH_DB}' ==="
  do_restore "${dump}" "${SCRATCH_DB}"

  log "=== 4/5 comparing the source and the restored copy ==="
  local source_sig restored_sig
  source_sig="$(table_signature "${db}")"
  restored_sig="$(table_signature "${SCRATCH_DB}")"

  if [[ "${source_sig}" != "${restored_sig}" ]]; then
    printf 'source:\n%s\nrestored:\n%s\n' "${source_sig}" "${restored_sig}" >&2
    fail "the restored database does not match the source"
  fi
  log "row counts match for every table ($(printf '%s' "${source_sig}" | wc -l | tr -d ' ') tables)"

  # The restored schema must also be usable by the application: Flyway's
  # history table has to be present and consistent.
  local migrations
  migrations="$(psql_host "${SCRATCH_DB}" "SELECT count(*) FROM flyway_schema_history WHERE success = true;")"
  [[ "${migrations}" -gt 0 ]] || fail "the restored database has no successful Flyway migrations"
  log "flyway history restored: ${migrations} successful migrations"

  log "=== 5/5 dropping the scratch database ==="
  docker exec -i "${PG_CONTAINER}" psql -U "${PG_USER}" -d postgres -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS \"${SCRATCH_DB}\" WITH (FORCE);" >/dev/null
  log "scratch database dropped"

  log "BACKUP/RESTORE VERIFIED OK"
  echo "${dump}"
}

case "${1:-}" in
  backup)  do_backup "${2:-}" ;;
  restore) do_restore "${2:-}" "${3:-}" ;;
  verify)  do_verify "${2:-}" ;;
  *)       fail "usage: $0 {backup|restore|verify} [args]" ;;
esac
