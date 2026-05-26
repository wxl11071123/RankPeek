#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${RANKPEEK_SMOKE_BASE_URL:-${1:-http://127.0.0.1:18080}}"
ADMIN_EMAIL="${RANKPEEK_SMOKE_ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${RANKPEEK_SMOKE_ADMIN_PASSWORD:-}"
EXPECTED_FLYWAY_VERSION="${RANKPEEK_SMOKE_EXPECTED_FLYWAY_VERSION:-9}"
EXPECTED_MODE="${RANKPEEK_SMOKE_EXPECT_MODE:-}"
EXPECTED_PUBLIC_REGISTRATION_ENABLED="${RANKPEEK_SMOKE_EXPECT_PUBLIC_REGISTRATION_ENABLED:-}"
EXPECTED_PASSWORD_RESET_EMAIL_ENABLED="${RANKPEEK_SMOKE_EXPECT_PASSWORD_RESET_EMAIL_ENABLED:-}"
EXPECTED_AI_ENABLED="${RANKPEEK_SMOKE_EXPECT_AI_ENABLED:-}"
EXPECTED_RATE_LIMIT_ENABLED="${RANKPEEK_SMOKE_EXPECT_RATE_LIMIT_ENABLED:-}"

log() {
  printf '[rankpeek-smoke] %s\n' "$1"
}

fail() {
  printf '[rankpeek-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

assert_optional_string() {
  local label="$1" expected="$2" actual="$3"
  if [[ -z "$expected" ]]; then
    return
  fi
  [[ "$actual" == "$expected" ]] || fail "Expected ${label}=${expected}, got ${actual}"
}

assert_optional_bool() {
  local label="$1" expected="$2" actual="$3"
  if [[ -z "$expected" ]]; then
    return
  fi
  if [[ "$expected" != "true" && "$expected" != "false" ]]; then
    fail "${label} expectation must be true or false"
  fi
  [[ "$actual" == "$expected" ]] || fail "Expected ${label}=${expected}, got ${actual}"
}

require_command curl
require_command jq

HEADERS_FILE="$(mktemp)"
trap 'rm -f "$HEADERS_FILE"' EXIT

log "Checking ${BASE_URL}/api/server/health"
HEALTH_BODY="$(
  curl -fsS \
    -D "$HEADERS_FILE" \
    -H "Accept: application/json" \
    -H "X-Request-Id: rankpeek-smoke-health" \
    "${BASE_URL}/api/server/health"
)"

printf '%s' "$HEALTH_BODY" | jq -e '.success == true and .data.status == "ok"' >/dev/null \
  || fail "Health endpoint did not return success=true and status=ok"

REQUEST_ID="$(
  awk -F': ' 'tolower($1) == "x-request-id" { gsub("\r", "", $2); print $2; exit }' "$HEADERS_FILE"
)"
if [[ -z "$REQUEST_ID" ]]; then
  fail "Health response did not include X-Request-Id"
fi
log "Health check passed with X-Request-Id=${REQUEST_ID}"

log "Checking ${BASE_URL}/api/server/version"
VERSION_BODY="$(curl -fsS -H "Accept: application/json" "${BASE_URL}/api/server/version")"
printf '%s' "$VERSION_BODY" | jq -e '.success == true and (.data.version | length > 0)' >/dev/null \
  || fail "Version endpoint did not return a version"
log "Version check passed: $(printf '%s' "$VERSION_BODY" | jq -r '.data.version')"

if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
  log "Skipping admin diagnostics; set RANKPEEK_SMOKE_ADMIN_EMAIL and RANKPEEK_SMOKE_ADMIN_PASSWORD to enable it"
  log "Smoke checks passed"
  exit 0
fi

log "Logging in as admin ${ADMIN_EMAIL}"
LOGIN_PAYLOAD="$(jq -nc --arg email "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" '{email: $email, password: $password}')"
LOGIN_BODY="$(
  curl -fsS \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    --data "$LOGIN_PAYLOAD" \
    "${BASE_URL}/api/auth/login"
)"
ACCESS_TOKEN="$(printf '%s' "$LOGIN_BODY" | jq -r '.data.accessToken // empty')"
if [[ -z "$ACCESS_TOKEN" ]]; then
  fail "Admin login did not return an access token"
fi

log "Checking admin diagnostics"
DIAGNOSTICS_BODY="$(
  curl -fsS \
    -H "Accept: application/json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/api/server/diagnostics"
)"

printf '%s' "$DIAGNOSTICS_BODY" | jq -e --arg expected "$EXPECTED_FLYWAY_VERSION" '
  .success == true
  and .data.status == "ok"
  and .data.database.status == "ok"
  and .data.flyway.status == "ok"
  and (.data.flyway.currentVersion | tostring) == $expected
' >/dev/null || fail "Diagnostics did not report ok database/flyway status at Flyway version ${EXPECTED_FLYWAY_VERSION}"

MODE="$(printf '%s' "$DIAGNOSTICS_BODY" | jq -r '.data.mode')"
PUBLIC_REGISTRATION_ENABLED="$(printf '%s' "$DIAGNOSTICS_BODY" | jq -r '.data.configuration.publicRegistrationEnabled')"
PASSWORD_RESET_EMAIL_ENABLED="$(printf '%s' "$DIAGNOSTICS_BODY" | jq -r '.data.configuration.passwordResetEmailEnabled')"
AI_ENABLED="$(printf '%s' "$DIAGNOSTICS_BODY" | jq -r '.data.configuration.aiEnabled')"
RATE_LIMIT_ENABLED="$(printf '%s' "$DIAGNOSTICS_BODY" | jq -r '.data.configuration.rateLimitEnabled')"

assert_optional_string "mode" "$EXPECTED_MODE" "$MODE"
assert_optional_bool "publicRegistrationEnabled" "$EXPECTED_PUBLIC_REGISTRATION_ENABLED" "$PUBLIC_REGISTRATION_ENABLED"
assert_optional_bool "passwordResetEmailEnabled" "$EXPECTED_PASSWORD_RESET_EMAIL_ENABLED" "$PASSWORD_RESET_EMAIL_ENABLED"
assert_optional_bool "aiEnabled" "$EXPECTED_AI_ENABLED" "$AI_ENABLED"
assert_optional_bool "rateLimitEnabled" "$EXPECTED_RATE_LIMIT_ENABLED" "$RATE_LIMIT_ENABLED"

log "Diagnostics check passed at Flyway version ${EXPECTED_FLYWAY_VERSION}"
log "Smoke checks passed"
