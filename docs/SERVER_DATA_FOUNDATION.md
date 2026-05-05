# RankPeek Server Data Foundation

This document describes the first `rankpeek-server` data foundation. It is intentionally limited to schema, deterministic mock services, mock AI output, and documentation.

## Scope

`rankpeek-server` is the future cloud service. It is not the Windows local agent.

`rankpeek-backend` continues to own LCU, SGP, cache fallback, match-history, and Electron SQLite local flows. This foundation does not refactor or call those flows.

Current privacy and integration boundaries:

- no user data collection;
- no user match-history upload;
- no LCU token or SGP token storage;
- no user private data storage;
- no real AI provider;
- no real payment provider;
- no real `101.qq.com` or `lpl.qq.com` crawler.

## Data Source Layers

A. Data Dragon / official patch data

This is the preferred source for structured patch identity, champion/item/rune metadata, and stable official version references. Future sync work should store raw source documents and derived patch changes separately.

B. `101.qq.com` CN meta data

CN meta data is modeled as snapshots. This phase only creates tables and mock records. Before real importing, the project needs a compliance and stability review covering allowed access patterns, rate limits, source attribution, and storage scope.

C. `lpl.qq.com` LPL professional match data

LPL data is modeled as matches, games, pick/ban rows, and player game stats. This phase only creates schema and mock records. Real importing requires separate research and compliance review.

D. AI search / Douyin / Bilibili / community content

Community and AI-search content should only be treated as human-curation leads. It should not automatically become trusted analysis. Future workflows should require review, source notes, and freshness checks before playstyle cards become active.

## Patch Knowledge Base

Core tables:

- `patch_versions`
- `patch_source_documents`
- `patch_changes`

The design separates patch identity, raw source evidence, and structured derived changes. This allows a future importer or manual reviewer to preserve source material while still exposing normalized changes to prompt-building services.

## CN Meta Snapshots

Core tables:

- `cn_meta_snapshots`
- `cn_champion_stats`
- `cn_champion_builds`

Snapshots are keyed by source, patch, queue, tier scope, role, and data date. Champion stats and builds reference snapshots. JSON-like build columns are `TEXT` for H2 compatibility and can later move to PostgreSQL `jsonb`.

## LPL Snapshots

Core tables:

- `lpl_matches`
- `lpl_games`
- `lpl_pick_bans`
- `lpl_player_game_stats`

The model keeps match metadata separate from individual games and player rows. This supports BO series and champion/role queries without scraping or storing any user data.

## Playstyle Cards

Core tables:

- `playstyle_cards`
- `playstyle_card_sources`
- `patch_relevance_rules`

Playstyle cards are curated recommendations for a patch, champion, and role. Each card has source evidence, confidence, freshness, status, and optional expiry. Cards are intended for pregame prompt context, not as automatic truth from raw web data.

## Freshness and Expiry Rules

- If a champion is changed, related cards should become stale or expired.
- If a core item is changed, related cards should become stale or expired.
- If a core rune is changed, related cards should become stale or expired.
- If a card has not been reviewed across two patch versions, it should default to hidden or stale.

The first implementation supports deterministic mock checks through `patch_relevance_rules` and `patch_changes`.

## API Foundation

All responses use the same JSON envelope:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

Current server endpoints:

- `GET /api/server/health`
- `GET /api/server/version`
- `GET /api/patch/current`
- `GET /api/patch/{patchKey}/changes`
- `GET /api/cn-meta/champions/{championId}?patchKey=&role=&tierScope=`
- `GET /api/esports/lpl/champions/{championId}?patchKey=&role=`
- `GET /api/playstyles/cards?patchKey=&championId=&role=`
- `POST /api/playstyles/cards/mock-seed`
- `POST /api/analysis/pregame/mock`

## Mock AI Boundary

`MockAiProvider` returns deterministic pregame analysis with:

- `cost.estimatedCredits = 0`
- `cost.chargedCredits = 0`
- `cost.mock = true`

It does not require an API key and does not perform network requests.

## Later Phases

1. Data Dragon sync.
2. `101.qq.com` importer research and compliance review.
3. `lpl.qq.com` importer research and compliance review.
4. Human review admin workflow for playstyle cards.
5. Real AI provider.
6. Account system.
7. Credits system.
8. Payment system.
9. Client integration.
