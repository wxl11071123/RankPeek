# rankpeek-server

`rankpeek-server` is the first cloud-server foundation module for RankPeek. It is separate from `rankpeek-backend`.

## Positioning

- `rankpeek-server` is the future Linux cloud service for account, credits, AI, patch knowledge, CN meta snapshots, LPL snapshots, and playstyle cards.
- `rankpeek-backend` remains the Windows local agent for LCU, SGP, local cache, match history, and Electron SQLite flows.
- This module does not replace, refactor, or call the stable local backend chain.

## Current Scope

This phase implements only a server data, auth foundation, CN meta sync foundation, and mock AI foundation:

- Spring Boot 3.x application on Java 21.
- Default local-dev port `18080`.
- Unified API response shape through `ApiResponse<T>`, `ApiError`, and `GlobalExceptionHandler`.
- Flyway migration `V1__server_foundation.sql`.
- Flyway migration `V2__auth_foundation.sql` for local account registration/login basics.
- Flyway migration `V3__cn_meta_sync_foundation.sql` for CN meta sync jobs and source documents.
- `V3__cn_meta_sync_foundation.sql` intentionally remains V3 because V2 is already used by auth; renaming it would break databases that have applied V3.
- H2-backed local-dev and test profiles.
- PostgreSQL driver and placeholder config comments for a future production database.
- Deterministic mock services for patch, CN meta, LPL usage, playstyle cards, prompt context, and pregame analysis.
- Disabled-by-default manual sample client boundary for public aggregate `101.qq.com` CN meta data.

## Boundaries

This module intentionally does not:

- run scheduled or matrix real `101.qq.com` sync;
- fetch real `lpl.qq.com` pages;
- call Riot, LCU, SGP, or Electron;
- upload or collect user match history;
- store LCU tokens, SGP tokens, or user private data;
- connect to DeepSeek, OpenAI, or any real AI model;
- integrate real payments or credits charging;
- verify email, recover passwords, use CAPTCHA, or provide third-party login;
- provide Docker, Kubernetes, or production deployment scripts.

Mock source URLs use the `mock://` scheme and exist only as deterministic test fixtures.

## Database Notes

Flyway creates the first schema for:

- patch knowledge and source documents;
- future CN meta snapshots and champion stats/builds;
- future LPL matches, games, pick/ban, and player game stats;
- playstyle cards, sources, and patch relevance rules.

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
- `POST /api/cn-meta/sync/real-once` is the only real-source entry point, and it requires `real-source-enabled=true`.
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

This sync foundation is limited to public aggregate champion statistics. It does not collect summoner names, PUUIDs, account IDs, personal match history, cookies, tokens, login state, or private game data. It does not implement CAPTCHA bypass, signature cracking, proxy pools, IP rotation, or high-concurrency crawling. Manual sync endpoints are currently unauthenticated foundation endpoints and must be protected with admin authorization before production use.

Before using the real sample client outside local development, the endpoint template, query parameters, and response fields must be manually confirmed from publicly accessible browser DevTools output. Production use also needs admin authorization, source attribution, frequency limits, and compliance review.

## Auth Foundation

`rankpeek-server` now supports a minimal local account foundation:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Passwords are stored with BCrypt hashes. Refresh tokens are generated as opaque random values, but only their SHA-256 hashes are stored in `auth_refresh_tokens`. Access tokens use a local HMAC JWT service with local-dev/test secrets from config; non-dev modes must provide a real secret through configuration. This foundation does not implement email verification, payments, credits, third-party login, or any real AI integration. It does not store LCU tokens, SGP tokens, match history, or private game data.

Production deployments can create or reset a first administrator at startup by setting:

- `RANKPEEK_INITIAL_ADMIN_ENABLED=true`
- `RANKPEEK_INITIAL_ADMIN_EMAIL`
- `RANKPEEK_INITIAL_ADMIN_PASSWORD`
- `RANKPEEK_INITIAL_ADMIN_DISPLAY_NAME`

This is disabled by default. When enabled, startup creates the configured email as an `ADMIN`, or updates an existing account to `ADMIN`, `ACTIVE`, and the configured password.

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

The first supported Ubuntu deployment shape is a Spring Boot jar running under systemd with local PostgreSQL. Production config lives in `src/main/resources/application-prod.yml` and reads secrets from environment variables.

See [`../docs/rankpeek-server-ubuntu-deployment.md`](../docs/rankpeek-server-ubuntu-deployment.md) for the deploy steps and the systemd/env templates under `deploy/ubuntu/`.

Useful endpoints:

- `GET /api/server/health`
- `GET /api/server/version`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/cn-meta/sync/mock-once`
- `POST /api/cn-meta/sync/real-once`
- `POST /api/cn-meta/sync/configured-matrix`
- `GET /api/cn-meta/sync/jobs`
- `GET /api/patch/current`
- `POST /api/analysis/pregame/mock`
- `POST /api/analysis/pregame/stream`

## Why Safe by Default

Real CN meta, LPL, AI, account, credits, and payment integrations need separate compliance, product, cost, privacy, and failure-mode reviews. This phase keeps CN meta real access limited to one manually triggered, disabled-by-default public aggregate sample path. It does not enable daily full sync or configured real matrix sync.
