#!/usr/bin/env bash
set -Eeuo pipefail

ENV_FILE="${RANKPEEK_PREFLIGHT_ENV_FILE:-${1:-/etc/rankpeek/rankpeek-server.env}}"

log() {
  printf '[rankpeek-preflight] %s\n' "$1"
}

warn() {
  printf '[rankpeek-preflight] WARN: %s\n' "$1" >&2
}

fail() {
  printf '[rankpeek-preflight] ERROR: %s\n' "$1" >&2
  exit 1
}

value_of() {
  local name="$1"
  printf '%s' "${!name-}"
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || fail "Env file not found: $path"
}

require_env_file_permissions() {
  local path="$1"
  local expected_owner="${RANKPEEK_PREFLIGHT_EXPECT_ENV_OWNER:-root}"
  local expected_group="${RANKPEEK_PREFLIGHT_EXPECT_ENV_GROUP:-rankpeek}"
  local expected_mode="${RANKPEEK_PREFLIGHT_EXPECT_ENV_MODE:-640}"
  local expected actual
  expected="${expected_owner}:${expected_group}:${expected_mode}"
  actual="$(stat -c '%U:%G:%a' "$path")" || fail "Could not inspect env file permissions: $path"
  [[ "$actual" == "$expected" ]] || fail "Expected ${path} permissions ${expected}, got ${actual}"
}

require_present() {
  local name="$1"
  local value
  value="$(value_of "$name")"
  [[ -n "$value" ]] || fail "${name} is required"
}

reject_placeholder() {
  local name="$1"
  local value
  value="$(value_of "$name")"
  [[ -n "$value" ]] || fail "${name} is required"
  case "$value" in
    CHANGE_ME*|*CHANGE_ME*|*change-me*|*example.com*)
      fail "${name} still contains a placeholder value"
      ;;
  esac
}

require_secret() {
  local name="$1"
  local minimum_length="${2:-32}"
  local value
  require_present "$name"
  reject_placeholder "$name"
  value="$(value_of "$name")"
  (( ${#value} >= minimum_length )) || fail "${name} must be at least ${minimum_length} characters"
}

require_equals() {
  local name="$1" expected="$2"
  local value
  value="$(value_of "$name")"
  [[ "$value" == "$expected" ]] || fail "Expected ${name}=${expected}, got ${value:-<empty>}"
}

require_bool() {
  local name="$1" expected="$2"
  local value
  value="$(value_of "$name")"
  if [[ "$value" != "true" && "$value" != "false" ]]; then
    fail "${name} must be true or false"
  fi
  [[ "$value" == "$expected" ]] || fail "Expected ${name}=${expected}, got ${value}"
}

enabled() {
  [[ "$(value_of "$1")" == "true" ]]
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

require_non_wildcard_cors() {
  local name="RANKPEEK_CORS_ALLOWED_ORIGINS"
  local origins origin trimmed
  require_present "$name"
  origins="$(value_of "$name")"
  IFS=',' read -r -a origin_list <<< "$origins"
  for origin in "${origin_list[@]}"; do
    trimmed="$(trim "$origin")"
    [[ -n "$trimmed" ]] || fail "${name} contains an empty origin"
    [[ "$trimmed" != "*" ]] || fail "${name} must not contain wildcard '*' in production"
    if [[ "$trimmed" == http://localhost:* || "$trimmed" == http://127.0.0.1:* ]]; then
      warn "${name} contains local origin ${trimmed}; verify this is intentional for the deployment"
    fi
  done
}

require_file "$ENV_FILE"
require_env_file_permissions "$ENV_FILE"
log "Loading ${ENV_FILE}"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

require_equals SPRING_PROFILES_ACTIVE prod
require_equals RANKPEEK_SERVER_ADDRESS 127.0.0.1
require_present RANKPEEK_SERVER_DB_URL
require_present RANKPEEK_SERVER_DB_USERNAME
require_present RANKPEEK_SERVER_DB_PASSWORD
reject_placeholder RANKPEEK_SERVER_DB_PASSWORD
require_secret RANKPEEK_AUTH_ACCESS_TOKEN_SECRET 32
require_non_wildcard_cors
require_bool RANKPEEK_PUBLIC_REGISTRATION_ENABLED false
require_bool RANKPEEK_RATE_LIMIT_ENABLED true

if enabled RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED; then
  require_present RANKPEEK_PASSWORD_RESET_EMAIL_FROM
  reject_placeholder RANKPEEK_PASSWORD_RESET_EMAIL_FROM
  require_present RANKPEEK_PASSWORD_RESET_URL_BASE
  reject_placeholder RANKPEEK_PASSWORD_RESET_URL_BASE
  require_present SPRING_MAIL_HOST
  require_present SPRING_MAIL_PORT
  require_present SPRING_MAIL_USERNAME
  reject_placeholder SPRING_MAIL_USERNAME
  require_present SPRING_MAIL_PASSWORD
  reject_placeholder SPRING_MAIL_PASSWORD
else
  warn "Password reset email is disabled; real users cannot self-serve password recovery"
fi

if enabled RANKPEEK_AI_ENABLED; then
  require_equals RANKPEEK_AI_PROVIDER deepseek
  require_present RANKPEEK_AI_BASE_URL
  require_present RANKPEEK_AI_MODEL
  require_present RANKPEEK_AI_API_KEY
  reject_placeholder RANKPEEK_AI_API_KEY
else
  warn "DeepSeek AI is disabled; AI-backed analysis endpoints will stay on mock/disabled behavior"
fi

if enabled RANKPEEK_INITIAL_ADMIN_ENABLED; then
  require_present RANKPEEK_INITIAL_ADMIN_EMAIL
  require_present RANKPEEK_INITIAL_ADMIN_PASSWORD
  reject_placeholder RANKPEEK_INITIAL_ADMIN_PASSWORD
  if [[ "$(value_of RANKPEEK_INITIAL_ADMIN_PASSWORD)" == "CHANGE_ME_INITIAL_ADMIN_PASSWORD" ]]; then
    fail "RANKPEEK_INITIAL_ADMIN_PASSWORD still contains CHANGE_ME_INITIAL_ADMIN_PASSWORD"
  fi
  warn "Initial admin bootstrap is enabled; disable it after the first successful production login"
fi

log "Preflight checks passed"
