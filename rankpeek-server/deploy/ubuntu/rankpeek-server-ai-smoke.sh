#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${RANKPEEK_AI_SMOKE_BASE_URL:-${1:-http://127.0.0.1:18080}}"
ADMIN_EMAIL="${RANKPEEK_AI_SMOKE_ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${RANKPEEK_AI_SMOKE_ADMIN_PASSWORD:-}"
USER_EMAIL="${RANKPEEK_AI_SMOKE_USER_EMAIL:-}"
USER_PASSWORD="${RANKPEEK_AI_SMOKE_USER_PASSWORD:-}"
EXPECTED_CHARGE="${RANKPEEK_AI_SMOKE_EXPECTED_CHARGE:-1}"
SKIP_GRANT="${RANKPEEK_AI_SMOKE_SKIP_GRANT:-false}"
RUN_SUFFIX="$(date -u +%Y%m%dT%H%M%SZ)-$$"
INPUT_HASH="${RANKPEEK_AI_SMOKE_INPUT_HASH:-ai-smoke-${RUN_SUFFIX}}"
GRANT_IDEMPOTENCY_KEY="${RANKPEEK_AI_SMOKE_GRANT_IDEMPOTENCY_KEY:-ai-smoke-grant-${RUN_SUFFIX}}"
COACH_IDEMPOTENCY_KEY="${RANKPEEK_AI_SMOKE_IDEMPOTENCY_KEY:-ai-smoke-coach-${RUN_SUFFIX}}"

log() {
  printf '[rankpeek-ai-smoke] %s\n' "$1"
}

fail() {
  printf '[rankpeek-ai-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_non_empty() {
  local name="$1" value="$2"
  [[ -n "$value" ]] || fail "${name} is required"
}

require_positive_integer() {
  local name="$1" value="$2"
  if ! [[ "$value" =~ ^[0-9]+$ ]] || (( value < 1 )); then
    fail "${name} must be a positive integer"
  fi
}

api_error() {
  jq -r '.error.code // "UNKNOWN_ERROR"' 2>/dev/null
}

api_error_message() {
  jq -r '.error.message // "no error message"' 2>/dev/null
}

logout_session() {
  local label="$1" refresh_token="$2"
  if [[ -z "$refresh_token" ]]; then
    return
  fi

  local payload
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
cleanup() {
  set +e
  logout_session "admin" "$ADMIN_REFRESH_TOKEN"
  logout_session "user" "$USER_REFRESH_TOKEN"
}
trap cleanup EXIT

login() {
  local email="$1" password="$2" body
  local payload
  payload="$(jq -nc --arg email "$email" --arg password "$password" '{email: $email, password: $password}')"
  body="$(
    curl -fsS \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      --data "$payload" \
      "${BASE_URL}/api/auth/login"
  )"
  printf '%s' "$body" | jq -e '.success == true and (.data.accessToken | length > 0) and (.data.refreshToken | length > 0)' >/dev/null \
    || fail "Login failed for ${email}: $(printf '%s' "$body" | api_error) $(printf '%s' "$body" | api_error_message)"
  printf '%s' "$body"
}

get_balance() {
  local access_token="$1" body
  body="$(
    curl -fsS \
      -H "Accept: application/json" \
      -H "Authorization: Bearer ${access_token}" \
      "${BASE_URL}/api/credits/balance"
  )"
  printf '%s' "$body" | jq -e '.success == true and (.data.balance | type == "number")' >/dev/null \
    || fail "Balance request failed: $(printf '%s' "$body" | api_error) $(printf '%s' "$body" | api_error_message)"
  printf '%s' "$body" | jq -r '.data.balance'
}

require_command curl
require_command jq
require_command date

require_non_empty RANKPEEK_AI_SMOKE_ADMIN_EMAIL "$ADMIN_EMAIL"
require_non_empty RANKPEEK_AI_SMOKE_ADMIN_PASSWORD "$ADMIN_PASSWORD"
require_non_empty RANKPEEK_AI_SMOKE_USER_EMAIL "$USER_EMAIL"
require_non_empty RANKPEEK_AI_SMOKE_USER_PASSWORD "$USER_PASSWORD"
require_positive_integer RANKPEEK_AI_SMOKE_EXPECTED_CHARGE "$EXPECTED_CHARGE"
GRANT_CREDITS="${RANKPEEK_AI_SMOKE_GRANT_CREDITS:-$((EXPECTED_CHARGE + 1))}"
require_positive_integer RANKPEEK_AI_SMOKE_GRANT_CREDITS "$GRANT_CREDITS"

log "Logging in admin and smoke user against ${BASE_URL}"
ADMIN_LOGIN_BODY="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
ADMIN_ACCESS_TOKEN="$(printf '%s' "$ADMIN_LOGIN_BODY" | jq -r '.data.accessToken')"
ADMIN_REFRESH_TOKEN="$(printf '%s' "$ADMIN_LOGIN_BODY" | jq -r '.data.refreshToken')"

USER_LOGIN_BODY="$(login "$USER_EMAIL" "$USER_PASSWORD")"
USER_ID="$(printf '%s' "$USER_LOGIN_BODY" | jq -r '.data.user.id')"
USER_ACCESS_TOKEN="$(printf '%s' "$USER_LOGIN_BODY" | jq -r '.data.accessToken')"
USER_REFRESH_TOKEN="$(printf '%s' "$USER_LOGIN_BODY" | jq -r '.data.refreshToken')"
[[ "$USER_ID" =~ ^[0-9]+$ ]] || fail "Smoke user login did not return a numeric user id"

BALANCE_BEFORE="$(get_balance "$USER_ACCESS_TOKEN")"
log "Smoke user ${USER_ID} balance before grant: ${BALANCE_BEFORE}"

if [[ "$SKIP_GRANT" != "true" ]]; then
  log "Granting ${GRANT_CREDITS} credits with idempotency key ${GRANT_IDEMPOTENCY_KEY}"
  GRANT_PAYLOAD="$(jq -nc \
    --argjson userId "$USER_ID" \
    --argjson amount "$GRANT_CREDITS" \
    '{userId: $userId, amount: $amount, reason: "production AI smoke"}')"
  GRANT_BODY="$(
    curl -fsS \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
      -H "X-RankPeek-Idempotency-Key: ${GRANT_IDEMPOTENCY_KEY}" \
      --data "$GRANT_PAYLOAD" \
      "${BASE_URL}/api/admin/credits/grants"
  )"
  printf '%s' "$GRANT_BODY" | jq -e '.success == true' >/dev/null \
    || fail "Credit grant failed: $(printf '%s' "$GRANT_BODY" | api_error) $(printf '%s' "$GRANT_BODY" | api_error_message)"
fi

BALANCE_AFTER_GRANT="$(get_balance "$USER_ACCESS_TOKEN")"
if (( BALANCE_AFTER_GRANT < EXPECTED_CHARGE )); then
  fail "Smoke user balance ${BALANCE_AFTER_GRANT} is lower than expected AI charge ${EXPECTED_CHARGE}"
fi
log "Smoke user balance before AI call: ${BALANCE_AFTER_GRANT}"

SYSTEM_PROMPT="$(cat <<'EOF'
You are RankPeek's production smoke-test coach summary generator.
Return only one JSON object. Do not return Markdown or code fences.
The JSON object must match coach_summary_report.v1 and analysisType coach_summary.
Use the inputHash supplied by the user prompt exactly.
Use ISO-8601 UTC for metadata.generatedAt.
EOF
)"

USER_PROMPT="$(cat <<EOF
Create a minimal coach_summary_report.v1 JSON report for this synthetic smoke-test snapshot.
inputHash: ${INPUT_HASH}
promptVersion: coach_summary.smoke.v1
snapshotSchemaVersion: coach_summary_input_snapshot.v2
dataQualityConfidence: medium

Required fields:
- schemaVersion: coach_summary_report.v1
- analysisType: coach_summary
- inputHash: ${INPUT_HASH}
- title, summary, verdict, keyFindings, trainingPlan, championAdvice, chartBlocks, warnings, metadata

Synthetic facts:
- 20 ranked matches, 11 wins, 9 losses, winRate 55.
- Primary role MID, 14 games.
- Champion Ahri MID: 8 games, 5 wins, averageKda 3.1.
- Main issue: deaths before neutral objectives.
- Training plan: next 5 games, track deaths before dragon or baron.
EOF
)"

COACH_REQUEST="$(jq -nc \
  --arg inputHash "$INPUT_HASH" \
  --arg systemPrompt "$SYSTEM_PROMPT" \
  --arg userPrompt "$USER_PROMPT" \
  '{
    inputHash: $inputHash,
    snapshotSchemaVersion: "coach_summary_input_snapshot.v2",
    promptVersion: "coach_summary.smoke.v1",
    dataQualityConfidence: "medium",
    systemPrompt: $systemPrompt,
    userPrompt: $userPrompt
  }')"

log "Calling coach-summary with idempotency key ${COACH_IDEMPOTENCY_KEY}"
FIRST_BODY="$(
  curl -fsS \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
    -H "X-RankPeek-Idempotency-Key: ${COACH_IDEMPOTENCY_KEY}" \
    --data "$COACH_REQUEST" \
    "${BASE_URL}/api/analysis/coach-summary"
)"
printf '%s' "$FIRST_BODY" | jq -e --arg inputHash "$INPUT_HASH" '
  .success == true
  and .data.report.schemaVersion == "coach_summary_report.v1"
  and .data.report.analysisType == "coach_summary"
  and .data.report.inputHash == $inputHash
  and (.data.report.title | length > 0)
  and (.data.report.summary | length > 0)
' >/dev/null || fail "Coach summary failed: $(printf '%s' "$FIRST_BODY" | api_error) $(printf '%s' "$FIRST_BODY" | api_error_message)"
FIRST_REPORT="$(printf '%s' "$FIRST_BODY" | jq -S '.data.report')"

BALANCE_AFTER_FIRST="$(get_balance "$USER_ACCESS_TOKEN")"
EXPECTED_BALANCE_AFTER_FIRST=$((BALANCE_AFTER_GRANT - EXPECTED_CHARGE))
if (( BALANCE_AFTER_FIRST != EXPECTED_BALANCE_AFTER_FIRST )); then
  fail "Expected balance ${EXPECTED_BALANCE_AFTER_FIRST} after AI charge, got ${BALANCE_AFTER_FIRST}"
fi
log "First AI call charged ${EXPECTED_CHARGE} credit(s)"

LEDGER_BODY="$(
  curl -fsS \
    -H "Accept: application/json" \
    -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
    "${BASE_URL}/api/credits/ledger"
)"
printf '%s' "$LEDGER_BODY" | jq -e --argjson expected "$EXPECTED_CHARGE" '
  .success == true
  and ([.data.entries[] | select(.type == "AI_CHARGE" and .amount == (0 - $expected))] | length >= 1)
' >/dev/null || fail "Credit ledger does not contain the expected AI_CHARGE entry"

log "Replaying coach-summary with the same idempotency key"
REPLAY_BODY="$(
  curl -fsS \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
    -H "X-RankPeek-Idempotency-Key: ${COACH_IDEMPOTENCY_KEY}" \
    --data "$COACH_REQUEST" \
    "${BASE_URL}/api/analysis/coach-summary"
)"
printf '%s' "$REPLAY_BODY" | jq -e --arg inputHash "$INPUT_HASH" '
  .success == true
  and .data.report.inputHash == $inputHash
' >/dev/null || fail "Coach summary replay failed: $(printf '%s' "$REPLAY_BODY" | api_error) $(printf '%s' "$REPLAY_BODY" | api_error_message)"
REPLAY_REPORT="$(printf '%s' "$REPLAY_BODY" | jq -S '.data.report')"
if [[ "$FIRST_REPORT" != "$REPLAY_REPORT" ]]; then
  fail "Replay report differed from the first coach-summary result"
fi

BALANCE_AFTER_REPLAY="$(get_balance "$USER_ACCESS_TOKEN")"
if (( BALANCE_AFTER_REPLAY != BALANCE_AFTER_FIRST )); then
  fail "Replay changed balance from ${BALANCE_AFTER_FIRST} to ${BALANCE_AFTER_REPLAY}"
fi
log "Replay returned the stored result without another credit charge"

RUNS_BODY="$(
  curl -fsS \
    -H "Accept: application/json" \
    -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
    "${BASE_URL}/api/analysis/runs?endpoint=coach-summary&status=SUCCEEDED&limit=10&offset=0"
)"
printf '%s' "$RUNS_BODY" | jq -e '.success == true and (.data.runs | type == "array")' >/dev/null \
  || fail "AI run list request failed: $(printf '%s' "$RUNS_BODY" | api_error) $(printf '%s' "$RUNS_BODY" | api_error_message)"

mapfile -t RUN_IDS < <(printf '%s' "$RUNS_BODY" | jq -r --argjson expected "$EXPECTED_CHARGE" '
  .data.runs[] | select(.status == "SUCCEEDED" and .chargedCredits == $expected) | .id
')

RUN_ID=""
RUN_DETAIL_BODY=""
for candidate_run_id in "${RUN_IDS[@]}"; do
  candidate_detail="$(
    curl -fsS \
      -H "Accept: application/json" \
      -H "Authorization: Bearer ${USER_ACCESS_TOKEN}" \
      "${BASE_URL}/api/analysis/runs/${candidate_run_id}"
  )"
  if printf '%s' "$candidate_detail" | jq -e --arg inputHash "$INPUT_HASH" --argjson expected "$EXPECTED_CHARGE" '
      .success == true
      and .data.status == "SUCCEEDED"
      and .data.chargedCredits == $expected
      and .data.response.report.inputHash == $inputHash
    ' >/dev/null; then
    RUN_ID="$candidate_run_id"
    RUN_DETAIL_BODY="$candidate_detail"
    break
  fi
done

[[ -n "$RUN_ID" && -n "$RUN_DETAIL_BODY" ]] || fail "Could not find the succeeded coach-summary AI run for inputHash ${INPUT_HASH}"

log "AI run ${RUN_ID} is queryable and matches the smoke response"
log "AI smoke checks passed"
