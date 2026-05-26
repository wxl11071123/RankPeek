#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${RANKPEEK_ADMIN_USER_SMOKE_BASE_URL:-${1:-http://127.0.0.1:18080}}"
ADMIN_EMAIL="${RANKPEEK_ADMIN_USER_SMOKE_ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${RANKPEEK_ADMIN_USER_SMOKE_ADMIN_PASSWORD:-}"
USER_EMAIL="${RANKPEEK_ADMIN_USER_SMOKE_USER_EMAIL:-}"
USER_PASSWORD="${RANKPEEK_ADMIN_USER_SMOKE_USER_PASSWORD:-}"
DISPLAY_NAME="${RANKPEEK_ADMIN_USER_SMOKE_DISPLAY_NAME:-RankPeek Smoke User}"
NORMALIZED_USER_EMAIL="$(printf '%s' "$USER_EMAIL" | tr '[:upper:]' '[:lower:]')"

log() {
  printf '[rankpeek-admin-user-smoke] %s\n' "$1"
}

fail() {
  printf '[rankpeek-admin-user-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_non_empty() {
  local name="$1" value="$2"
  [[ -n "$value" ]] || fail "${name} is required"
}

api_error() {
  jq -r '.error.code // "UNKNOWN_ERROR"' 2>/dev/null
}

api_error_message() {
  jq -r '.error.message // "no error message"' 2>/dev/null
}

logout_session() {
  local label="$1" refresh_token="$2" payload
  if [[ -z "$refresh_token" ]]; then
    return
  fi

  payload="$(jq -nc --arg refreshToken "$refresh_token" '{refreshToken: $refreshToken}')"
  curl -fsS \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    --data "$payload" \
    "${BASE_URL}/api/auth/logout" >/dev/null \
    || log "Logout failed for ${label}; revoke the session manually if this repeats"
}

ADMIN_REFRESH_TOKEN=""
USER_REFRESH_TOKEN=""
CREATE_BODY_FILE=""
cleanup() {
  set +e
  logout_session "admin" "$ADMIN_REFRESH_TOKEN"
  logout_session "user" "$USER_REFRESH_TOKEN"
  if [[ -n "$CREATE_BODY_FILE" ]]; then
    rm -f "$CREATE_BODY_FILE"
  fi
}
trap cleanup EXIT

login() {
  local email="$1" password="$2" payload body
  payload="$(jq -nc --arg email "$email" --arg password "$password" '{email: $email, password: $password}')"
  if ! body="$(
    curl -fsS \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      --data "$payload" \
      "${BASE_URL}/api/auth/login"
  )"; then
    fail "Login request failed for ${email}"
  fi
  printf '%s' "$body" | jq -e '.success == true and (.data.accessToken | length > 0) and (.data.refreshToken | length > 0)' >/dev/null \
    || fail "Login failed for ${email}: $(printf '%s' "$body" | api_error) $(printf '%s' "$body" | api_error_message)"
  printf '%s' "$body"
}

require_command curl
require_command jq
require_command tr

require_non_empty RANKPEEK_ADMIN_USER_SMOKE_ADMIN_EMAIL "$ADMIN_EMAIL"
require_non_empty RANKPEEK_ADMIN_USER_SMOKE_ADMIN_PASSWORD "$ADMIN_PASSWORD"
require_non_empty RANKPEEK_ADMIN_USER_SMOKE_USER_EMAIL "$USER_EMAIL"
require_non_empty RANKPEEK_ADMIN_USER_SMOKE_USER_PASSWORD "$USER_PASSWORD"
require_non_empty RANKPEEK_ADMIN_USER_SMOKE_DISPLAY_NAME "$DISPLAY_NAME"

log "Logging in admin ${ADMIN_EMAIL} against ${BASE_URL}"
ADMIN_LOGIN_BODY="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
ADMIN_ACCESS_TOKEN="$(printf '%s' "$ADMIN_LOGIN_BODY" | jq -r '.data.accessToken')"
ADMIN_REFRESH_TOKEN="$(printf '%s' "$ADMIN_LOGIN_BODY" | jq -r '.data.refreshToken')"

log "Creating or verifying internal user ${NORMALIZED_USER_EMAIL}"
CREATE_BODY_FILE="$(mktemp)"
CREATE_PAYLOAD="$(jq -nc \
  --arg email "$USER_EMAIL" \
  --arg password "$USER_PASSWORD" \
  --arg displayName "$DISPLAY_NAME" \
  '{email: $email, password: $password, displayName: $displayName}')"
if ! CREATE_STATUS="$(
  curl -sS \
    -o "$CREATE_BODY_FILE" \
    -w "%{http_code}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
    --data "$CREATE_PAYLOAD" \
    "${BASE_URL}/api/admin/users"
)"; then
  fail "Admin create-user request failed"
fi
CREATE_BODY="$(cat "$CREATE_BODY_FILE")"

if [[ "$CREATE_STATUS" == "200" || "$CREATE_STATUS" == "201" ]]; then
  printf '%s' "$CREATE_BODY" | jq -e --arg email "$NORMALIZED_USER_EMAIL" '
    .success == true
    and .data.email == $email
    and .data.role == "USER"
    and .data.status == "ACTIVE"
    and .data.lastLoginAt == null
  ' >/dev/null || fail "Create-user response did not describe the expected ACTIVE USER"
  log "Internal user was created"
elif [[ "$CREATE_STATUS" == "409" ]] && printf '%s' "$CREATE_BODY" | jq -e '.error.code == "EMAIL_ALREADY_REGISTERED"' >/dev/null; then
  log "Internal user already exists; verifying login with the supplied password"
else
  fail "Create-user failed with HTTP ${CREATE_STATUS}: $(printf '%s' "$CREATE_BODY" | api_error) $(printf '%s' "$CREATE_BODY" | api_error_message)"
fi

log "Logging in internal user ${NORMALIZED_USER_EMAIL}"
USER_LOGIN_BODY="$(login "$USER_EMAIL" "$USER_PASSWORD")"
USER_REFRESH_TOKEN="$(printf '%s' "$USER_LOGIN_BODY" | jq -r '.data.refreshToken')"
printf '%s' "$USER_LOGIN_BODY" | jq -e --arg email "$NORMALIZED_USER_EMAIL" '
  .success == true
  and .data.user.email == $email
  and .data.user.role == "USER"
  and .data.user.status == "ACTIVE"
' >/dev/null || fail "Internal user login returned unexpected user metadata"

log "Admin-created internal user login verified"
