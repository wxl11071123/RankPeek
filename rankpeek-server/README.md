# rankpeek-server

`rankpeek-server` is the first cloud-server foundation module for RankPeek. It is separate from `rankpeek-backend`.

## Positioning

- `rankpeek-server` is the future Linux cloud service for account, credits, AI, patch knowledge, CN meta snapshots, LPL snapshots, and playstyle cards.
- `rankpeek-backend` remains the Windows local agent for LCU, SGP, local cache, match history, and Electron SQLite flows.
- This module does not replace, refactor, or call the stable local backend chain.

## Current Scope

This phase implements only a server data and mock AI foundation:

- Spring Boot 3.x application on Java 21.
- Default local-dev port `18080`.
- Unified API response shape through `ApiResponse<T>`, `ApiError`, and `GlobalExceptionHandler`.
- Flyway migration `V1__server_foundation.sql`.
- H2-backed local-dev and test profiles.
- PostgreSQL driver and placeholder config comments for a future production database.
- Deterministic mock services for patch, CN meta, LPL usage, playstyle cards, prompt context, and pregame analysis.

## Boundaries

This module intentionally does not:

- fetch real `101.qq.com` or `lpl.qq.com` pages;
- call Riot, LCU, SGP, or Electron;
- upload or collect user match history;
- store LCU tokens, SGP tokens, or user private data;
- connect to DeepSeek, OpenAI, or any real AI model;
- integrate real payments or credits charging;
- provide Docker, Kubernetes, or production deployment scripts.

Mock source URLs use the `mock://` scheme and exist only as deterministic test fixtures.

## Database Notes

Flyway creates the first schema for:

- patch knowledge and source documents;
- future CN meta snapshots and champion stats/builds;
- future LPL matches, games, pick/ban, and player game stats;
- playstyle cards, sources, and patch relevance rules.

JSON-like columns are stored as `TEXT` in this phase so H2 tests remain simple and stable. A future PostgreSQL migration can convert selected columns to `jsonb` after real importer requirements are reviewed.

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

Useful endpoints:

- `GET /api/server/health`
- `GET /api/server/version`
- `GET /api/patch/current`
- `POST /api/analysis/pregame/mock`
- `POST /api/analysis/pregame/stream`

## Why Mock Only

Real CN meta, LPL, AI, account, credits, and payment integrations need separate compliance, product, cost, privacy, and failure-mode reviews. This phase keeps the foundation deterministic and reversible, so later stages can add real providers behind clear boundaries.
