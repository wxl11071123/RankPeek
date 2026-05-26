# rankpeek-server

`rankpeek-server` is the first cloud-server foundation module for RankPeek. It is separate from `rankpeek-backend`.

## Positioning

- `rankpeek-server` is the future Linux cloud service for account, credits, AI, patch knowledge, CN meta snapshots, LPL snapshots, and playstyle cards.
- `rankpeek-backend` remains the Windows local agent for LCU, SGP, local cache, match history, and Electron SQLite flows.
- This module does not replace, refactor, or call the stable local backend chain.

## Current Scope

This phase implements a server data, auth, admin, credits, CN meta sync, and mock-first AI foundation:

- Spring Boot 3.x application on Java 21.
- Default local-dev port `18080`.
- Unified API response shape through `ApiResponse<T>`, `ApiError`, and `GlobalExceptionHandler`.
- Flyway migration `V1__server_foundation.sql`.
- Flyway migration `V2__auth_foundation.sql` for local account registration/login basics.
- Flyway migration `V3__cn_meta_sync_foundation.sql` for CN meta sync jobs and source documents.
- Flyway migration `V7__credits_foundation.sql` for user credit balances, credit ledger entries, and AI analysis run tracking.
- Flyway migration `V8__ai_analysis_run_results.sql` for AI run request hashes, replayable success results, failure messages, and charge/refund ledger links.
- Flyway migration `V9__password_reset_tokens.sql` for hashed password reset tokens.
- `V3__cn_meta_sync_foundation.sql` intentionally remains V3 because V2 is already used by auth; renaming it would break databases that have applied V3.
- H2-backed local-dev and test profiles.
- PostgreSQL production profile, Ubuntu systemd, Nginx reverse-proxy, PostgreSQL backup, and monitoring templates, plus deployment smoke scripts.
- Deterministic mock services for patch, CN meta, LPL usage, playstyle cards, prompt context, and pregame/postgame analysis.
- Disabled-by-default DeepSeek chat-completion provider for analysis streams.
- Disabled-by-default manual sample client boundary for public aggregate `101.qq.com` CN meta data.
- Enabled-by-default application rate limiting for auth and AI-cost endpoints.

## Boundaries

This module intentionally does not:

- run scheduled or matrix real `101.qq.com` sync;
- fetch real `lpl.qq.com` pages;
- call Riot, LCU, SGP, or Electron;
- upload or collect user match history;
- store LCU tokens, SGP tokens, or user private data;
- enable real AI by default or call any provider other than the explicitly configured DeepSeek chat-completion provider;
- integrate real payments;
- verify email, send real password reset email by default, use CAPTCHA, or provide third-party login;
- provide Docker, Kubernetes, managed load balancers, or multi-host production ingress.

Mock source URLs use the `mock://` scheme and exist only as deterministic test fixtures.

## Database Notes

Flyway creates the first schema for:

- patch knowledge and source documents;
- future CN meta snapshots and champion stats/builds;
- future LPL matches, games, pick/ban, and player game stats;
- playstyle cards, sources, and patch relevance rules;
- auth users, refresh tokens, password reset tokens, credit balances, credit ledger entries, and AI analysis runs.

JSON-like columns are stored as `TEXT` in this phase so H2 tests remain simple and stable. A future PostgreSQL migration can convert selected columns to `jsonb` after real importer requirements are reviewed.

## CN Meta Sync Foundation

`rankpeek-server` now includes a disabled-by-default CN meta sync foundation for public aggregate champion statistics:

- `rankpeek.cn-meta.sync.enabled=false` by default.
- The configured default source is `mock`.
- Tests and local defaults do not request real `101.qq.com` URLs.
- The current mock source writes deterministic public aggregate champion rows into `cn_meta_snapshots` and `cn_champion_stats`.
- `cn_meta_sync_jobs` records sync attempts, status, row counts, request counts, hashes, and errors.
- `cn_meta_source_documents` stores the fetched mock source document for each job with a repeatable content hash.

The real `101.qq.com` source now exists only as a disabled-by-default manual sample client:

- `rankpeek.cn-meta.sync.real-source-enabled=false` by default, including test.
- `rankpeek.cn-meta.sync.real-endpoint-template` is intentionally empty. RankPeek does not guess or hard-code the `101.qq.com` public endpoint in this repo.
- `POST /api/cn-meta/sync/real-once` is the only real-source entry point, and it requires `real-source-enabled=true` plus an `ADMIN` bearer token.
- The scheduler and `configured-matrix` endpoint do not run the real source.
- The real client sends no cookies, no login state, and no personal player identifiers. It uses a plain RankPeek development user agent, short timeouts, and a response byte limit.
- HTTP `401`, `403`, `429`, CAPTCHA, or risk-control content stops the job instead of retrying hard.
- The confirmed `101` `getRankFieldAverage` payload is rank/tier-level champion aggregate data. It is not lane- or role-specific, even when a browser page URL contains a lane value.
- Real `101` aggregate champion rows are stored with `role=ALL` and `data_source_note=101 getRankFieldAverage aggregate; role=ALL; not lane-specific`.
- Champion meta queries still accept role-specific client requests. If an exact role row is missing, the server may fall back to `role=ALL`, and the returned row keeps `role=ALL`.
- RankPeek must not describe `101` aggregate rows as `TOP`, `JUNGLE`, `MID`, `ADC`, or `SUPPORT` meta unless a separate role-specific source is added later.

For local manual verification only, a confirmed public endpoint template can be supplied outside the repo defaults:

```yaml
rankpeek:
  cn-meta:
    sync:
      real-source-enabled: true
      real-endpoint-template: "https://x1-6833.native.qq.com/x1/6833/1061022&0ce227?championid={championId}&time_type={timeType}&tier={tierCode}&dtstatdate={dataDate}"
      real-tier-code-map:
        PLATINUM: "20"
```

Supported real endpoint placeholders are `{patchKey}`, `{queueId}`, `{tierScope}`, `{role}`, `{championId}`, `{timeType}`, `{tierCode}`, and `{dataDate}`. The confirmed `101` template currently uses `{championId}`, `{timeType}`, `{tierCode}`, and `{dataDate}`. Only `PLATINUM -> 20` has been confirmed; unconfigured tiers fail before any HTTP request with a `CN_META_TIER_CODE_MISSING` source error. The `role` request parameter on `real-once` is accepted only for compatibility and validation; real `101` syncs store and return `role=ALL`.

This sync foundation is limited to public aggregate champion statistics. It does not collect summoner names, PUUIDs, account IDs, personal match history, cookies, tokens, login state, or private game data. It does not implement CAPTCHA bypass, signature cracking, proxy pools, IP rotation, or high-concurrency crawling. Manual sync and sync job endpoints require an `ADMIN` bearer token.

Before using the real sample client outside local development, the endpoint template, query parameters, and response fields must be manually confirmed from publicly accessible browser DevTools output. Production use also needs source attribution, frequency limits, and compliance review.

## Auth Foundation

`rankpeek-server` now supports a minimal local account foundation:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/confirm`
- `GET /api/auth/me`

Passwords are stored with BCrypt hashes. Refresh tokens are generated as opaque random values, but only their SHA-256 hashes are stored in `auth_refresh_tokens`. `POST /api/auth/refresh` rotates refresh tokens and rejects reuse of the previous token. Password reset tokens are generated as opaque random values, stored only as SHA-256 hashes in `auth_password_reset_tokens`, expire after `RANKPEEK_AUTH_PASSWORD_RESET_TOKEN_TTL_SECONDS`, and revoke active refresh-token sessions after a successful reset. Password reset email delivery is disabled by default; when `RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=true`, the server sends reset links through Spring Mail using configured SMTP settings and fails startup if the sender address or reset URL base is missing. Access tokens use a local HMAC JWT service with local-dev/test secrets from config; non-dev modes must provide a real secret through configuration. This foundation does not implement email verification, payments, third-party login, CAPTCHA, or real password reset email delivery by default. It does not store LCU tokens, SGP tokens, match history, or private game data.

To enable password reset email in a deployed environment, set:

- `RANKPEEK_PASSWORD_RESET_EMAIL_ENABLED=true`
- `RANKPEEK_PASSWORD_RESET_EMAIL_FROM=no-reply@example.com`
- `RANKPEEK_PASSWORD_RESET_URL_BASE=https://rankpeek.example.com/password-reset`
- `RANKPEEK_PASSWORD_RESET_EMAIL_SUBJECT=RankPeek password reset` or another subject
- `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, and SMTP auth/TLS properties for the chosen mail provider

Public registration is enabled for local development and tests, but production defaults it off through `RANKPEEK_PUBLIC_REGISTRATION_ENABLED=false`. For an internal MVP, create the first admin through the initial-admin bootstrap and grant access intentionally instead of leaving open signup enabled. Production CORS is controlled by `RANKPEEK_CORS_ALLOWED_ORIGINS` and should list only trusted renderer or reverse-proxy origins.

Application-level rate limiting is enabled by default outside tests. It applies fixed-window limits to registration, login, refresh-token, password reset, `coach-summary`, and DeepSeek-backed analysis stream endpoints. Defaults are controlled by `RANKPEEK_RATE_LIMIT_WINDOW_SECONDS`, `RANKPEEK_RATE_LIMIT_AUTH_MAX_REQUESTS`, and `RANKPEEK_RATE_LIMIT_AI_MAX_REQUESTS`; exceeded requests return HTTP `429` with error code `RATE_LIMIT_EXCEEDED` and `Retry-After`.

Production deployments can create or reset a first administrator at startup by setting:

- `RANKPEEK_INITIAL_ADMIN_ENABLED=true`
- `RANKPEEK_INITIAL_ADMIN_EMAIL`
- `RANKPEEK_INITIAL_ADMIN_PASSWORD`
- `RANKPEEK_INITIAL_ADMIN_DISPLAY_NAME`

This is disabled by default in application config, but the Ubuntu env example enables it for the first production bootstrap. When enabled, startup creates the configured email as an `ADMIN`, or updates an existing account to `ADMIN`, `ACTIVE`, and the configured password. After a verified admin login, later deployments can set `RANKPEEK_INITIAL_ADMIN_ENABLED=false`; the production preflight then requires `RANKPEEK_PREFLIGHT_EXISTING_ADMIN_CONFIRMED=true` to avoid launching an internal MVP with no admin access path.

Admin user operations require an `ADMIN` bearer token:

- `GET /api/server/diagnostics`
- `GET /api/admin/users`
- `PATCH /api/admin/users/{userId}`
- `POST /api/admin/users/{userId}/sessions/revoke`
- `POST /api/admin/credits/grants`
- `POST /api/playstyles/cards/mock-seed`

Admins can list users, promote/demote other accounts, disable users, and revoke refresh-token sessions. Disabling a user revokes that user's active refresh tokens. The server prevents an admin from disabling or demoting their own account through the admin user endpoint.

## Credits Foundation

The server now tracks user credit balances and immutable ledger entries:

- `GET /api/credits/balance`
- `GET /api/credits/ledger`
- `POST /api/admin/credits/grants`

Admin credit grants require `X-RankPeek-Idempotency-Key` to avoid duplicate adjustments. `POST /api/analysis/coach-summary` requires a user bearer token when DeepSeek is enabled, reserves the configured credit charge, records token usage on success, refunds on upstream failure, and supports `X-RankPeek-Idempotency-Key`. DeepSeek-backed `pregame/stream` and `postgame/stream` also require a user bearer token, reserve `RANKPEEK_CREDITS_AI_STREAM_CHARGE_CREDITS`, store AI run metadata and token usage, and refund the charge on upstream failure.

For `coach-summary`, the idempotency key is scoped to the user. A succeeded run with the same request hash replays the stored AI response without another DeepSeek call or credit charge. A refunded failure with the same request hash replays the stored failure. A different request body with the same key returns `IDEMPOTENCY_KEY_CONFLICT`, and an in-progress reservation returns `AI_RUN_IN_PROGRESS`.

Users can query their own AI runs:

- `GET /api/analysis/runs?endpoint=&status=&limit=&offset=`
- `GET /api/analysis/runs/{runId}`

Admins can query all AI run metadata without returned report bodies:

- `GET /api/admin/analysis/runs?userId=&endpoint=&status=&limit=&offset=`
- `GET /api/admin/analysis/runs/{runId}`

The server stores `request_hash` and successful `response_json` for replay, but it does not persist raw `systemPrompt`, `userPrompt`, or request JSON.

## AI Provider

Analysis streams stay on the existing endpoints:

- `POST /api/analysis/pregame/stream`
- `POST /api/analysis/postgame/stream`
- `POST /api/analysis/coach-summary`

By default, these endpoints use deterministic mock output and do not call external AI services. When DeepSeek is enabled, stream endpoints become authenticated, billable AI requests; unauthenticated or insufficient-credit calls fail before the provider is contacted. To enable DeepSeek in a deployed environment, set:

- `RANKPEEK_AI_ENABLED=true`
- `RANKPEEK_AI_PROVIDER=deepseek`
- `RANKPEEK_AI_BASE_URL=https://api.deepseek.com`
- `RANKPEEK_AI_MODEL=deepseek-v4-flash` or another configured DeepSeek model
- `RANKPEEK_AI_API_KEY`
- `RANKPEEK_CREDITS_AI_STREAM_CHARGE_CREDITS=1` or another intentional stream charge

The DeepSeek client uses OpenAI-compatible streaming chat completions at `/chat/completions`. Errors such as missing keys, non-2xx upstream responses, timeouts, or malformed streams are returned to the frontend as `error` SSE events without logging or returning the API key, and billable stream reservations are refunded.

## Run Tests

```powershell
cd rankpeek-server
mvn test
```

The test profile uses H2 and Flyway. Tests must not require external network services.

## Run Locally

```powershell
cd rankpeek-server
mvn spring-boot:run
```

The default local-dev server listens on `http://localhost:18080`.

`src/main/resources/application.yml` is the local-dev profile baseline and uses an in-memory H2 database. It is not the Ubuntu production configuration.

## Ubuntu Production Deployment

The first supported Ubuntu deployment shape is a Spring Boot jar running under systemd with local PostgreSQL and an Nginx HTTPS reverse proxy. Production config lives in `src/main/resources/application-prod.yml` and reads secrets from environment variables.

See [`../docs/rankpeek-server-ubuntu-deployment.md`](../docs/rankpeek-server-ubuntu-deployment.md) for the deploy steps and the systemd/env templates under `deploy/ubuntu/`. Use [`../docs/rankpeek-server-production-launch-checklist.md`](../docs/rankpeek-server-production-launch-checklist.md) as the final go-live checklist for the first Ubuntu deployment, and [`../docs/rankpeek-server-production-launch-notes-template.md`](../docs/rankpeek-server-production-launch-notes-template.md) to record launch evidence without storing real secrets.

Every `/api/**` response includes `X-Request-Id`. Clients may provide one for support flows; otherwise the server generates one and writes an `api_request` access log line with method, path, status, duration, and request id.

Run `deploy/ubuntu/rankpeek-server-preflight.sh` before starting the production service; it rejects placeholder secrets, placeholder initial admin email values, missing dependent SMTP/AI/admin values, wildcard CORS, disabled rate limiting, open public registration, missing admin access path, and unsafe env file ownership and mode for the internal MVP deployment shape. After deploying the jar and systemd service, run `deploy/ubuntu/rankpeek-server-smoke.sh` on the Ubuntu host. It verifies public health/version endpoints and `X-Request-Id`; with `RANKPEEK_SMOKE_ADMIN_EMAIL` and `RANKPEEK_SMOKE_ADMIN_PASSWORD`, it also checks admin diagnostics, Flyway version `9`, and optional expected config switches such as `RANKPEEK_SMOKE_EXPECT_MODE`, `RANKPEEK_SMOKE_EXPECT_PUBLIC_REGISTRATION_ENABLED`, `RANKPEEK_SMOKE_EXPECT_INITIAL_ADMIN_ENABLED`, `RANKPEEK_SMOKE_EXPECT_PASSWORD_RESET_EMAIL_ENABLED`, `RANKPEEK_SMOKE_EXPECT_AI_ENABLED`, and `RANKPEEK_SMOKE_EXPECT_RATE_LIMIT_ENABLED`. When real AI is intentionally enabled, run `deploy/ubuntu/rankpeek-server-ai-smoke.sh` to verify credits, DeepSeek, and coach-summary idempotency. The Ubuntu templates also include PostgreSQL backup and restore-drill scripts under `deploy/ubuntu/postgres/`, plus a five-minute production monitor under `deploy/ubuntu/monitoring/`.

Useful endpoints:

- `GET /api/server/health`
- `GET /api/server/version`
- `GET /api/server/diagnostics` (ADMIN bearer token)
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/confirm`
- `GET /api/auth/me` (bearer token)
- `GET /api/admin/users` (ADMIN bearer token)
- `PATCH /api/admin/users/{userId}` (ADMIN bearer token)
- `POST /api/admin/users/{userId}/sessions/revoke` (ADMIN bearer token)
- `GET /api/credits/balance` (bearer token)
- `GET /api/credits/ledger` (bearer token)
- `POST /api/admin/credits/grants` (ADMIN bearer token)
- `GET /api/analysis/runs` (bearer token)
- `GET /api/analysis/runs/{runId}` (bearer token)
- `GET /api/admin/analysis/runs` (ADMIN bearer token)
- `GET /api/admin/analysis/runs/{runId}` (ADMIN bearer token)
- `POST /api/cn-meta/sync/mock-once` (ADMIN bearer token)
- `POST /api/cn-meta/sync/real-once` (ADMIN bearer token)
- `POST /api/cn-meta/sync/configured-matrix` (ADMIN bearer token)
- `GET /api/cn-meta/sync/jobs` (ADMIN bearer token)
- `GET /api/patch/current`
- `POST /api/playstyles/cards/mock-seed` (ADMIN bearer token)
- `POST /api/analysis/pregame/mock`
- `POST /api/analysis/pregame/stream`
- `POST /api/analysis/postgame/stream`
- `POST /api/analysis/coach-summary` (bearer token)

## Why Safe by Default

Real CN meta, LPL, AI, account, credits, and payment integrations need separate compliance, product, cost, privacy, and failure-mode reviews. This phase keeps CN meta real access limited to one manually triggered, disabled-by-default public aggregate sample path and keeps DeepSeek disabled unless explicit environment variables enable it. It does not enable daily full sync or configured real matrix sync.
