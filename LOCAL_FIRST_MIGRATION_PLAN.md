# Local-First Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move RankPeek from a cloud-account product to a local-first desktop product while preserving AI analysis as the core feature, supporting user-selected AI providers, local usage/cost tracking, OP.GG data, CN meta comparison, and removal of login/register/credits.

**Architecture:** `rankpeek-backend` becomes the only service boundary for packaged desktop builds. `rankpeek-frontend` talks to `http://127.0.0.1:8080` for LCU, SGP, AI, OP.GG, CN meta, and cost ledgers. `rankpeek-server` becomes reference code only; its auth, credits, email, admin, and billing flows are removed from the shipping app.

**Tech Stack:** Electron, Vue 3, TypeScript, Node test runner, Spring Boot 3.5, Java 21, H2 local database, JdbcTemplate, OkHttp, Jackson, GraalVM native image.

---

## Product Decisions

- The packaged app must not require a RankPeek account.
- The packaged app must not call `https://api.rankpeek.cn`.
- AI stays in the product and becomes BYOK: users configure provider, base URL, model, and API key locally.
- The first provider implementation is OpenAI-compatible chat completions because DeepSeek, SiliconFlow, OpenRouter, and many free/low-cost proxy providers can use the same request shape.
- DeepSeek Mainland pricing stays as the default cost preset:
  - `deepseek-v4-flash`: cache hit `0.02`, cache miss input `1`, output `2` CNY per 1M tokens.
  - `deepseek-v4-pro`: cache hit `0.025`, cache miss input `3`, output `6` CNY per 1M tokens.
- Custom providers can record usage with unknown cost, or with user-entered input/output pricing.
- OP.GG scraping must run in `rankpeek-backend`, not in the renderer, to avoid CORS and to centralize caching/throttling.
- Local backend H2 is the source of truth for local AI runs, cost events, OP.GG cache, CN meta cache, and manual cost entries.
- `rankpeek-server` should remain buildable during the migration until a later cleanup decision, but the desktop product must stop depending on it.

## File Structure Map

### Frontend Files To Modify

- `rankpeek-frontend/src/renderer/services/rankpeekServerClient.ts`: convert from cloud server client to local data client or replace imports with a new local client.
- `rankpeek-frontend/src/renderer/services/rankpeekServerClient.test.ts`: update default base URL and endpoint expectations.
- `rankpeek-frontend/src/renderer/services/rankpeekAuthClient.ts`: delete after all imports are removed.
- `rankpeek-frontend/src/renderer/services/rankpeekAuthClient.test.ts`: delete after auth removal.
- `rankpeek-frontend/src/renderer/services/rankpeekCreditsClient.ts`: replace with local cost/usage client, then delete.
- `rankpeek-frontend/src/renderer/services/rankpeekCreditsClient.test.ts`: replace with local cost/usage tests, then delete.
- `rankpeek-frontend/src/renderer/services/gamingAiServerStream.ts`: switch to local AI pregame stream, remove auth and refresh.
- `rankpeek-frontend/src/renderer/services/gamingAiServerStream.test.ts`: update for no-login local AI behavior and provider errors.
- `rankpeek-frontend/src/renderer/services/postgameAiServerStream.ts`: switch to local AI postgame stream, remove auth and refresh.
- `rankpeek-frontend/src/renderer/services/postgameAiServerStream.test.ts`: update for local endpoint and local usage events.
- `rankpeek-frontend/src/renderer/services/coachSummaryAiClient.ts`: switch to local backend `/api/v1/ai/coach-summary`, remove auth.
- `rankpeek-frontend/src/renderer/services/coachSummaryAiClient.test.ts`: update request and error assertions.
- `rankpeek-frontend/src/renderer/views/SettingsView.vue`: replace account/register/reset UI with AI provider settings and cost settings.
- `rankpeek-frontend/src/renderer/views/SettingsView.test.ts`: replace auth UI assertions with provider settings assertions.
- `rankpeek-frontend/src/renderer/views/AiAnalysisView.vue`: replace account/credits view with local AI run and cost ledger view.
- `rankpeek-frontend/src/renderer/views/AiAnalysisView.test.ts`: update import and rendering assertions.
- `rankpeek-frontend/src/renderer/views/OpggWindowView.vue`: keep UX, switch all data calls to local backend.
- `rankpeek-frontend/src/renderer/index.html`: remove `https://api.rankpeek.cn` from CSP `connect-src`.
- `rankpeek-frontend/package.json`: keep scripts unchanged unless tests require new targeted entries.

### Frontend Files To Create

- `rankpeek-frontend/src/renderer/services/rankpeekLocalServiceClient.ts`: local base URL, diagnostics, shared JSON parsing.
- `rankpeek-frontend/src/renderer/services/localAiProviderClient.ts`: provider settings, provider test call, masked config retrieval.
- `rankpeek-frontend/src/renderer/services/localCostClient.ts`: cost summary, AI usage events, manual costs.
- `rankpeek-frontend/src/renderer/services/localAiStreamClient.ts`: shared SSE/NDJSON parser for pregame and postgame local AI.
- `rankpeek-frontend/src/renderer/services/localAiPricing.ts`: frontend display of known provider pricing presets.
- `rankpeek-frontend/src/renderer/services/localAiProviderClient.test.ts`
- `rankpeek-frontend/src/renderer/services/localCostClient.test.ts`
- `rankpeek-frontend/src/renderer/services/localAiStreamClient.test.ts`

### Local Backend Files To Modify

- `rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java`: add local AI, provider, OP.GG, CN meta, and cost tables.
- `rankpeek-backend/src/main/java/io/rankpeek/config/WebConfig.java`: confirm local frontend CORS allows all new local endpoints.
- `rankpeek-backend/src/main/java/io/rankpeek/config/LocalDatabaseConfig.java`: no architecture change expected; keep H2 as local store.
- `rankpeek-backend/src/main/resources/application.yml`: add local AI defaults, OP.GG cache defaults, CN meta sync defaults.
- `rankpeek-backend/pom.xml`: add no dependency unless tests require an HTTP test helper; prefer JDK `HttpServer`.

### Local Backend Files To Create

- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderProfile.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderSettings.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderSettingsRepository.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderSettingsService.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderController.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderTestRequest.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderTestResponse.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/OpenAiCompatibleChatClient.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/OpenAiChatMessage.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiTokenUsage.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderException.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiController.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiAnalysisService.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiAnalysisStreamer.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiRunRepository.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiRun.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiRunResponse.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/PregameAnalysisRequest.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/PostgameAnalysisRequest.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/CoachSummaryAnalysisRequest.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/CoachSummaryAnalysisResponse.java`
- `rankpeek-backend/src/main/java/io/rankpeek/ai/CoachSummaryReportValidator.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/AiCostBreakdown.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/AiPricing.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/AiPricingCatalog.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/AiCostCalculator.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/CostEvent.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/CostRepository.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/CostService.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/CostController.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/ManualCostItem.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/ManualCostRequest.java`
- `rankpeek-backend/src/main/java/io/rankpeek/cost/CostSummaryResponse.java`
- `rankpeek-backend/src/main/java/io/rankpeek/opgg/*`: local copies/adaptations of server OP.GG models, cache repository, source client, service, and controller.
- `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/*`: local copies/adaptations of server CN meta models, repositories, sync service, and controller.
- `rankpeek-backend/src/main/java/io/rankpeek/patch/*`: local patch metadata support copied/adapted from server.
- `rankpeek-backend/src/main/java/io/rankpeek/esports/*`: local LPL champion usage support copied/adapted from server if prompt context still needs it.
- `rankpeek-backend/src/test/java/io/rankpeek/ai/*Test.java`
- `rankpeek-backend/src/test/java/io/rankpeek/cost/*Test.java`
- `rankpeek-backend/src/test/java/io/rankpeek/opgg/*Test.java`
- `rankpeek-backend/src/test/java/io/rankpeek/cnmeta/*Test.java`

### Cloud Server Files To Leave Alone Initially

- `rankpeek-server/src/main/java/io/rankpeek/server/auth/*`
- `rankpeek-server/src/main/java/io/rankpeek/server/credits/*`
- `rankpeek-server/src/main/java/io/rankpeek/server/analysis/*`
- `rankpeek-server/src/main/java/io/rankpeek/server/opgg/*`
- `rankpeek-server/src/main/java/io/rankpeek/server/cnmeta/*`
- `rankpeek-server/src/main/java/io/rankpeek/server/cost/*`

Do not delete server code in the first migration branch. Use it as source material and keep `mvn test` available for regression reference.

---

## Endpoint Contract

### Local AI Provider Endpoints

```text
GET  /api/v1/ai/providers
GET  /api/v1/ai/settings
PUT  /api/v1/ai/settings
POST /api/v1/ai/test
```

`GET /api/v1/ai/providers` returns known presets and a custom option:

```json
{
  "success": true,
  "data": {
    "providers": [
      {
        "id": "deepseek",
        "label": "DeepSeek",
        "dialect": "openai-compatible",
        "defaultBaseUrl": "https://api.deepseek.com",
        "models": ["deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"],
        "supportsPromptCacheUsage": true
      },
      {
        "id": "custom-openai-compatible",
        "label": "Custom OpenAI-compatible",
        "dialect": "openai-compatible",
        "defaultBaseUrl": "",
        "models": [],
        "supportsPromptCacheUsage": false
      }
    ]
  }
}
```

`PUT /api/v1/ai/settings` accepts:

```json
{
  "enabled": true,
  "providerId": "deepseek",
  "baseUrl": "https://api.deepseek.com",
  "model": "deepseek-v4-flash",
  "apiKey": "sk-user-owned-key",
  "saveApiKey": true,
  "temperature": 0.4,
  "maxTokens": 4096,
  "pricing": {
    "currency": "CNY",
    "inputCacheHitCnyPerMillionTokens": 0.02,
    "inputCacheMissCnyPerMillionTokens": 1,
    "outputCnyPerMillionTokens": 2
  }
}
```

`GET /api/v1/ai/settings` never returns the raw key:

```json
{
  "success": true,
  "data": {
    "enabled": true,
    "providerId": "deepseek",
    "baseUrl": "https://api.deepseek.com",
    "model": "deepseek-v4-flash",
    "apiKeySaved": true,
    "apiKeyMasked": "sk-...abcd",
    "temperature": 0.4,
    "maxTokens": 4096,
    "pricing": {
      "currency": "CNY",
      "inputCacheHitCnyPerMillionTokens": 0.02,
      "inputCacheMissCnyPerMillionTokens": 1,
      "outputCnyPerMillionTokens": 2
    }
  }
}
```

### Local AI Analysis Endpoints

```text
POST /api/v1/ai/pregame/stream
POST /api/v1/ai/postgame/stream
POST /api/v1/ai/coach-summary
GET  /api/v1/ai/runs?endpoint=&status=&limit=&offset=
GET  /api/v1/ai/runs/{runId}
```

Streaming endpoints emit SSE events compatible with the existing frontend parser:

```text
event: start
data: {"type":"start","title":"RankPeek AI stream started"}

event: section
data: {"type":"section","title":"AI analysis"}

event: delta
data: {"type":"delta","text":"..."}

event: usage
data: {"type":"usage","usage":{"provider":"deepseek","model":"deepseek-v4-flash","promptTokens":1000,"completionTokens":500,"totalTokens":1500,"promptCacheHitTokens":200,"promptCacheMissTokens":800,"cost":{"currency":"CNY","totalCny":0.00176}}}

event: done
data: {"type":"done"}
```

### Local Cost Endpoints

```text
GET  /api/v1/costs/summary?from=2026-05-01&to=2026-05-31
GET  /api/v1/costs/events?type=&limit=&offset=
POST /api/v1/costs/manual
GET  /api/v1/costs/manual
PATCH /api/v1/costs/manual/{id}
DELETE /api/v1/costs/manual/{id}
```

Manual cost request:

```json
{
  "label": "OP.GG proxy subscription",
  "category": "data",
  "amountCny": 30,
  "cadence": "monthly",
  "effectiveDate": "2026-05-31",
  "note": "User-entered local cost"
}
```

### Local Data Endpoints

```text
GET  /api/v1/opgg/champions?mode=&region=&tier=
GET  /api/v1/opgg/champions/{championId}/detail?mode=&region=&tier=&position=
GET  /api/v1/cn-meta/champions/{championId}/latest?tierScope=
GET  /api/v1/cn-meta/champions/{championId}
POST /api/v1/cn-meta/sync/real-once
GET  /api/v1/cn-meta/sync/jobs
GET  /api/v1/patch/current
GET  /api/v1/patch/{patchKey}/changes
GET  /api/v1/esports/lpl/champions/{championId}
```

---

## Task 1: Establish Local Service Client Boundary

**Files:**
- Create: `rankpeek-frontend/src/renderer/services/rankpeekLocalServiceClient.ts`
- Create: `rankpeek-frontend/src/renderer/services/rankpeekLocalServiceClient.test.ts`
- Modify: `rankpeek-frontend/src/renderer/services/rankpeekServerClient.ts`
- Modify: `rankpeek-frontend/src/renderer/services/rankpeekServerClient.test.ts`
- Modify: `rankpeek-frontend/src/renderer/index.html`

- [x] **Step 1: Write failing local base URL tests**

Create `rankpeek-frontend/src/renderer/services/rankpeekLocalServiceClient.test.ts` with assertions that:

```ts
import assert from 'node:assert/strict'
import test from 'node:test'
import {
  DEFAULT_RANKPEEK_LOCAL_SERVICE_BASE_URL,
  normalizeRankPeekLocalServiceBaseUrl
} from './rankpeekLocalServiceClient.ts'

test('local service defaults to packaged backend port', () => {
  assert.equal(DEFAULT_RANKPEEK_LOCAL_SERVICE_BASE_URL, 'http://127.0.0.1:8080')
  assert.equal(normalizeRankPeekLocalServiceBaseUrl(undefined), 'http://127.0.0.1:8080')
  assert.equal(normalizeRankPeekLocalServiceBaseUrl(''), 'http://127.0.0.1:8080')
  assert.equal(normalizeRankPeekLocalServiceBaseUrl('http://127.0.0.1:8080///'), 'http://127.0.0.1:8080')
})
```

- [x] **Step 2: Run the failing test**

Run:

```powershell
cd rankpeek-frontend
node --test src/renderer/services/rankpeekLocalServiceClient.test.ts
```

Expected: FAIL because `rankpeekLocalServiceClient.ts` does not exist.

- [x] **Step 3: Add the local service client**

Create `rankpeek-frontend/src/renderer/services/rankpeekLocalServiceClient.ts` with:

```ts
export const DEFAULT_RANKPEEK_LOCAL_SERVICE_BASE_URL = 'http://127.0.0.1:8080'

export const RANKPEEK_LOCAL_SERVICE_BASE_URL = normalizeRankPeekLocalServiceBaseUrl(
  import.meta.env?.VITE_RANKPEEK_LOCAL_SERVICE_BASE_URL
)

export function normalizeRankPeekLocalServiceBaseUrl(value: string | undefined): string {
  const trimmed = value?.trim()
  if (!trimmed) {
    return DEFAULT_RANKPEEK_LOCAL_SERVICE_BASE_URL
  }
  return trimmed.replace(/\/+$/, '')
}

export interface LocalApiResponse<T> {
  success?: boolean
  data?: T
  error?: {
    code?: string
    message?: string
  } | null
}

export async function parseLocalJson<T>(response: Response): Promise<LocalApiResponse<T>> {
  try {
    return await response.json() as LocalApiResponse<T>
  } catch {
    return {}
  }
}
```

- [x] **Step 4: Keep compatibility exports while callers migrate**

Modify `rankpeekServerClient.ts` so existing OP.GG and CN meta functions use `RANKPEEK_LOCAL_SERVICE_BASE_URL`. Keep `RANKPEEK_SERVER_BASE_URL` as an alias during migration:

```ts
import {
  RANKPEEK_LOCAL_SERVICE_BASE_URL,
  normalizeRankPeekLocalServiceBaseUrl
} from './rankpeekLocalServiceClient.ts'

export const DEFAULT_RANKPEEK_SERVER_BASE_URL = 'http://127.0.0.1:8080'
export const RANKPEEK_SERVER_BASE_URL = RANKPEEK_LOCAL_SERVICE_BASE_URL
export const RANKPEEK_SERVER_DIAGNOSTICS_ENDPOINT = '/api/v1/system/identity'

export const normalizeRankPeekServerBaseUrl = normalizeRankPeekLocalServiceBaseUrl
```

Update OP.GG endpoints from `/api/opgg/...` to `/api/v1/opgg/...` and CN meta endpoints from `/api/cn-meta/...` to `/api/v1/cn-meta/...`.

- [x] **Step 5: Remove cloud CSP permission**

In `rankpeek-frontend/src/renderer/index.html`, remove `https://api.rankpeek.cn` from `connect-src`. Keep `ws://127.0.0.1:8080`, `http://127.0.0.1:8080`, and local HTTPS loopback entries.

- [x] **Step 6: Run frontend client tests**

Run:

```powershell
cd rankpeek-frontend
node --test src/renderer/services/rankpeekLocalServiceClient.test.ts
node --test src/renderer/services/rankpeekServerClient.test.ts
```

Expected: PASS after test expectations are updated to local defaults and `/api/v1` endpoints.

- [x] **Step 7: Commit**

```powershell
git add rankpeek-frontend/src/renderer/services/rankpeekLocalServiceClient.ts rankpeek-frontend/src/renderer/services/rankpeekLocalServiceClient.test.ts rankpeek-frontend/src/renderer/services/rankpeekServerClient.ts rankpeek-frontend/src/renderer/services/rankpeekServerClient.test.ts rankpeek-frontend/src/renderer/index.html
git commit -m "refactor(frontend): route cloud data clients to local service"
```

---

## Task 2: Add Local AI Provider Settings

**Files:**
- Modify: `rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderProfile.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderSettings.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderSettingsRepository.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderSettingsService.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderController.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/ai/AiProviderSettingsServiceTest.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/ai/AiProviderControllerTest.java`

- [x] **Step 1: Write schema tests for AI settings**

Add a backend test that instantiates `LocalCacheSchemaInitializer`, runs schema creation, and verifies `ai_provider_settings` exists with columns:

```sql
id VARCHAR(64) PRIMARY KEY
enabled BOOLEAN
provider_id VARCHAR(128)
base_url VARCHAR(1000)
model VARCHAR(255)
api_key_encrypted CLOB
api_key_masked VARCHAR(128)
temperature DOUBLE
max_tokens INT
pricing_raw_json CLOB
updated_at BIGINT
```

- [x] **Step 2: Run the failing backend test**

Run:

```powershell
cd rankpeek-backend
mvn -Dtest=AiProviderSettingsServiceTest test
```

Expected: FAIL because the table and service do not exist.

- [x] **Step 3: Add AI settings schema**

In `LocalCacheSchemaInitializer.createTables()`, add:

```java
jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS ai_provider_settings (
            id VARCHAR(64) PRIMARY KEY,
            enabled BOOLEAN,
            provider_id VARCHAR(128),
            base_url VARCHAR(1000),
            model VARCHAR(255),
            api_key_encrypted CLOB,
            api_key_masked VARCHAR(128),
            temperature DOUBLE,
            max_tokens INT,
            pricing_raw_json CLOB,
            updated_at BIGINT
        )
        """);
```

In `migrateTables()`, add `ADD COLUMN IF NOT EXISTS` calls for each column above.

- [x] **Step 4: Add provider records**

Create `AiProviderProfile`:

```java
package io.rankpeek.ai;

import java.util.List;

public record AiProviderProfile(
        String id,
        String label,
        String dialect,
        String defaultBaseUrl,
        List<String> models,
        boolean supportsPromptCacheUsage
) {
}
```

Create `AiProviderSettings`:

```java
package io.rankpeek.ai;

public record AiProviderSettings(
        boolean enabled,
        String providerId,
        String baseUrl,
        String model,
        boolean apiKeySaved,
        String apiKeyMasked,
        Double temperature,
        int maxTokens,
        String pricingRawJson
) {
}
```

- [x] **Step 5: Implement repository upsert and read**

`AiProviderSettingsRepository` must use `JdbcTemplate` and a single row id `default`. Reads return an empty optional when no settings exist. Saves use H2 `MERGE INTO ai_provider_settings KEY(id) VALUES (...)`.

- [x] **Step 6: Implement service defaults**

`AiProviderSettingsService` must:

- Return DeepSeek as default provider when no row exists.
- Normalize base URLs by trimming trailing slashes.
- Reject blank model when AI is enabled.
- Mask keys as first 3 characters plus last 4 characters for values longer than 8 characters.
- Never return raw API keys from public responses.

- [x] **Step 7: Add controller**

`AiProviderController` exposes:

```java
@RestController
@RequestMapping("/api/v1/ai")
class AiProviderController {
    @GetMapping("/providers")
    ApiResponse<Map<String, Object>> providers()

    @GetMapping("/settings")
    ApiResponse<AiProviderSettings> settings()

    @PutMapping("/settings")
    ApiResponse<AiProviderSettings> save(@RequestBody AiProviderSettingsSaveRequest request)
}
```

Use the existing local backend `io.rankpeek.model.ApiResponse`.

- [x] **Step 8: Run tests**

Run:

```powershell
cd rankpeek-backend
mvn -Dtest=AiProviderSettingsServiceTest,AiProviderControllerTest test
```

Expected: PASS.

- [x] **Step 9: Commit**

```powershell
git add rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java rankpeek-backend/src/main/java/io/rankpeek/ai rankpeek-backend/src/test/java/io/rankpeek/ai
git commit -m "feat(backend): add local AI provider settings"
```

---

## Task 3: Implement OpenAI-Compatible AI Client

**Files:**
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/OpenAiCompatibleChatClient.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/OpenAiChatMessage.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/AiTokenUsage.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderException.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/ai/OpenAiCompatibleChatClientTest.java`

- [x] **Step 1: Write streaming parser tests**

Use JDK `com.sun.net.httpserver.HttpServer` in `OpenAiCompatibleChatClientTest`. The fake server returns:

```text
data: {"model":"deepseek-v4-flash","choices":[{"delta":{"content":"hello"}}]}

data: {"model":"deepseek-v4-flash","choices":[{"delta":{"content":" world"}}]}

data: {"model":"deepseek-v4-flash","usage":{"prompt_tokens":10,"prompt_cache_hit_tokens":4,"prompt_cache_miss_tokens":6,"completion_tokens":3,"total_tokens":13},"choices":[{"delta":{}}]}

data: [DONE]
```

Assert collected text is `hello world` and usage has:

```text
provider=deepseek
model=deepseek-v4-flash
promptTokens=10
promptCacheHitTokens=4
promptCacheMissTokens=6
completionTokens=3
totalTokens=13
```

- [x] **Step 2: Run the failing test**

```powershell
cd rankpeek-backend
mvn -Dtest=OpenAiCompatibleChatClientTest test
```

Expected: FAIL because the client does not exist.

- [x] **Step 3: Add request/usage records**

Create:

```java
package io.rankpeek.ai;

public record OpenAiChatMessage(String role, String content) {
}
```

```java
package io.rankpeek.ai;

public record AiTokenUsage(
        String provider,
        String model,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long promptCacheHitTokens,
        long promptCacheMissTokens
) {
}
```

```java
package io.rankpeek.ai;

public class AiProviderException extends RuntimeException {
    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [x] **Step 4: Implement client**

`OpenAiCompatibleChatClient` must:

- POST to `{baseUrl}/chat/completions`.
- Send `Authorization: Bearer {apiKey}`.
- Send `stream=true`.
- Send `stream_options: {"include_usage": true}`.
- Include `response_format: {"type": "json_object"}` when the caller requests JSON.
- Parse SSE `data:` lines.
- Ignore empty lines and comments.
- Stop on `[DONE]`.
- Extract `choices[0].delta.content`.
- Extract usage fields using OpenAI/DeepSeek-compatible field names.
- Derive `promptCacheMissTokens = max(0, promptTokens - promptCacheHitTokens)` when the provider omits miss tokens.

- [x] **Step 5: Run tests**

```powershell
cd rankpeek-backend
mvn -Dtest=OpenAiCompatibleChatClientTest test
```

Expected: PASS.

- [x] **Step 6: Commit**

```powershell
git add rankpeek-backend/src/main/java/io/rankpeek/ai/OpenAiCompatibleChatClient.java rankpeek-backend/src/main/java/io/rankpeek/ai/OpenAiChatMessage.java rankpeek-backend/src/main/java/io/rankpeek/ai/AiTokenUsage.java rankpeek-backend/src/main/java/io/rankpeek/ai/AiProviderException.java rankpeek-backend/src/test/java/io/rankpeek/ai/OpenAiCompatibleChatClientTest.java
git commit -m "feat(backend): add OpenAI-compatible AI streaming client"
```

---

## Task 4: Move AI Analysis Endpoints Into Local Backend

**Files:**
- Modify: `rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiController.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiAnalysisService.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiAnalysisStreamer.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiRunRepository.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiRun.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/LocalAiRunResponse.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/PregameAnalysisRequest.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/PostgameAnalysisRequest.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/CoachSummaryAnalysisRequest.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/ai/CoachSummaryAnalysisResponse.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/ai/LocalAiControllerTest.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/ai/LocalAiRunRepositoryTest.java`
- Reference: `rankpeek-server/src/main/java/io/rankpeek/server/analysis/DeepSeekAnalysisStreamer.java`
- Reference: `rankpeek-server/src/main/java/io/rankpeek/server/analysis/AnalysisController.java`

- [x] **Step 1: Write endpoint tests**

`LocalAiControllerTest` must assert:

- `POST /api/v1/ai/pregame/stream` does not require `Authorization`.
- `POST /api/v1/ai/postgame/stream` does not require `Authorization`.
- `POST /api/v1/ai/coach-summary` does not require `Authorization`.
- Missing provider settings returns a local user-facing error code `AI_PROVIDER_NOT_CONFIGURED`.
- Successful stream emits `start`, at least one `delta`, `usage`, and `done`.

- [x] **Step 2: Run failing tests**

```powershell
cd rankpeek-backend
mvn -Dtest=LocalAiControllerTest,LocalAiRunRepositoryTest test
```

Expected: FAIL because local endpoints and tables do not exist.

- [x] **Step 3: Add local AI run schema**

Add `ai_analysis_runs` to `LocalCacheSchemaInitializer`:

```java
jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS ai_analysis_runs (
            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            endpoint VARCHAR(64),
            provider VARCHAR(128),
            model VARCHAR(255),
            status VARCHAR(32),
            request_hash VARCHAR(128),
            request_raw_json CLOB,
            response_raw_json CLOB,
            error_code VARCHAR(128),
            error_message VARCHAR(2000),
            prompt_tokens BIGINT DEFAULT 0,
            prompt_cache_hit_tokens BIGINT DEFAULT 0,
            prompt_cache_miss_tokens BIGINT DEFAULT 0,
            completion_tokens BIGINT DEFAULT 0,
            total_tokens BIGINT DEFAULT 0,
            input_cache_hit_cny DECIMAL(18,12) DEFAULT 0,
            input_cache_miss_cny DECIMAL(18,12) DEFAULT 0,
            output_cny DECIMAL(18,12) DEFAULT 0,
            total_cny DECIMAL(18,12) DEFAULT 0,
            created_at BIGINT,
            updated_at BIGINT
        )
        """);
```

Add indexes:

```java
jdbcTemplate.execute("""
        CREATE INDEX IF NOT EXISTS idx_ai_analysis_runs_recent
        ON ai_analysis_runs(created_at DESC)
        """);
```

- [x] **Step 4: Port request records**

Copy the shapes of server request records into local backend package `io.rankpeek.ai`:

- `PregameAnalysisRequest`
- `PostgameAnalysisRequest`
- `CoachSummaryAnalysisRequest`
- `CoachSummaryAnalysisResponse`

Keep JSON field names identical to current frontend payloads.

- [x] **Step 5: Port streamer behavior**

Adapt the useful parts of `DeepSeekAnalysisStreamer` into `LocalAiAnalysisStreamer`:

- Rename DeepSeek-specific class names to AI-generic names.
- Use `OpenAiCompatibleChatClient`.
- Use configured provider settings.
- Keep existing pregame player verdict and postgame prompt formatting.
- Keep coach summary JSON validation strict.
- Emit frontend-compatible SSE events.

- [x] **Step 6: Implement run persistence**

`LocalAiRunRepository` must:

- Insert run at `started` status before provider call.
- Update to `succeeded` with usage and raw response on success.
- Update to `failed` with code/message on failure.
- List by endpoint/status with limit/offset.
- Fetch by id.

- [x] **Step 7: Run backend AI tests**

```powershell
cd rankpeek-backend
mvn -Dtest=LocalAiControllerTest,LocalAiRunRepositoryTest,OpenAiCompatibleChatClientTest,AiProviderSettingsServiceTest test
```

Expected: PASS.

- [x] **Step 8: Commit**

```powershell
git add rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java rankpeek-backend/src/main/java/io/rankpeek/ai rankpeek-backend/src/test/java/io/rankpeek/ai
git commit -m "feat(backend): serve AI analysis from local backend"
```

---

## Task 5: Add Local Cost And Usage Ledger

**Files:**
- Modify: `rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/AiCostBreakdown.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/AiPricing.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/AiPricingCatalog.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/AiCostCalculator.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/CostEvent.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/CostRepository.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/CostService.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/CostController.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/ManualCostItem.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/ManualCostRequest.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cost/CostSummaryResponse.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/cost/AiCostCalculatorTest.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/cost/CostControllerTest.java`
- Reference: `rankpeek-server/src/main/java/io/rankpeek/server/cost/*`

- [x] **Step 1: Write pricing tests**

`AiCostCalculatorTest` must assert:

```text
deepseek-v4-flash: 100 cache-hit tokens, 200 cache-miss tokens, 300 output tokens => CNY 0.000800002
deepseek-v4-pro: 100 cache-hit tokens, 200 cache-miss tokens, 300 output tokens => CNY 0.0024000025
custom pricing: input hit 0, input miss 2, output 8 uses the same per-million formula
unknown pricing: totalCny is null and usage is still recorded
```

- [x] **Step 2: Run failing tests**

```powershell
cd rankpeek-backend
mvn -Dtest=AiCostCalculatorTest,CostControllerTest test
```

Expected: FAIL because cost package and tables do not exist.

- [x] **Step 3: Add cost tables**

Add:

```java
jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS cost_events (
            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            event_type VARCHAR(64),
            provider VARCHAR(128),
            model VARCHAR(255),
            source VARCHAR(128),
            amount_cny DECIMAL(18,12),
            currency VARCHAR(16),
            quantity BIGINT,
            metadata_raw_json CLOB,
            created_at BIGINT
        )
        """);

jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS manual_cost_items (
            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            label VARCHAR(255),
            category VARCHAR(128),
            amount_cny DECIMAL(18,12),
            cadence VARCHAR(32),
            effective_date VARCHAR(32),
            note VARCHAR(2000),
            active BOOLEAN,
            created_at BIGINT,
            updated_at BIGINT
        )
        """);
```

- [x] **Step 4: Port cost calculator**

Adapt server cost classes into local backend:

- `DeepSeekMainlandPricing` becomes `AiPricing`.
- `DeepSeekMainlandPricingCatalog` becomes `AiPricingCatalog`.
- `DeepSeekMainlandCostCalculator` becomes `AiCostCalculator`.

Keep exact DeepSeek Mainland rates listed in Product Decisions.

- [x] **Step 5: Record AI cost after usage**

In `LocalAiAnalysisService`, after successful usage parsing:

- Calculate `AiCostBreakdown`.
- Update `ai_analysis_runs` cost columns.
- Insert one `cost_events` row with `event_type='ai_analysis'`.
- Store usage and cost details in `metadata_raw_json`.

- [x] **Step 6: Add manual cost endpoints**

`CostController` exposes:

- `GET /api/v1/costs/summary`
- `GET /api/v1/costs/events`
- `POST /api/v1/costs/manual`
- `GET /api/v1/costs/manual`
- `PATCH /api/v1/costs/manual/{id}`
- `DELETE /api/v1/costs/manual/{id}`

Recurring cost math:

- `one_time`: counted if `effectiveDate` is in the requested range.
- `monthly`: counted once per month overlapping the requested range.
- `yearly`: counted as `amountCny / 12` in monthly summaries and once per year in yearly summaries.

- [x] **Step 7: Run tests**

```powershell
cd rankpeek-backend
mvn -Dtest=AiCostCalculatorTest,CostControllerTest,LocalAiControllerTest test
```

Expected: PASS.

- [x] **Step 8: Commit**

```powershell
git add rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java rankpeek-backend/src/main/java/io/rankpeek/cost rankpeek-backend/src/main/java/io/rankpeek/ai rankpeek-backend/src/test/java/io/rankpeek/cost rankpeek-backend/src/test/java/io/rankpeek/ai
git commit -m "feat(backend): track local AI and manual costs"
```

---

## Task 6: Migrate Frontend AI To Local Provider Flow

**Files:**
- Create: `rankpeek-frontend/src/renderer/services/localAiProviderClient.ts`
- Create: `rankpeek-frontend/src/renderer/services/localAiProviderClient.test.ts`
- Create: `rankpeek-frontend/src/renderer/services/localAiStreamClient.ts`
- Create: `rankpeek-frontend/src/renderer/services/localAiStreamClient.test.ts`
- Create: `rankpeek-frontend/src/renderer/services/localCostClient.ts`
- Create: `rankpeek-frontend/src/renderer/services/localCostClient.test.ts`
- Modify: `rankpeek-frontend/src/renderer/services/gamingAiServerStream.ts`
- Modify: `rankpeek-frontend/src/renderer/services/gamingAiServerStream.test.ts`
- Modify: `rankpeek-frontend/src/renderer/services/postgameAiServerStream.ts`
- Modify: `rankpeek-frontend/src/renderer/services/postgameAiServerStream.test.ts`
- Modify: `rankpeek-frontend/src/renderer/services/coachSummaryAiClient.ts`
- Modify: `rankpeek-frontend/src/renderer/services/coachSummaryAiClient.test.ts`
- Modify: `rankpeek-frontend/src/renderer/views/SettingsView.vue`
- Modify: `rankpeek-frontend/src/renderer/views/SettingsView.test.ts`
- Modify: `rankpeek-frontend/src/renderer/views/AiAnalysisView.vue`
- Modify: `rankpeek-frontend/src/renderer/views/AiAnalysisView.test.ts`

- [x] **Step 1: Write client tests**

Tests must assert:

- `localAiProviderClient.getLocalAiSettings()` calls `/api/v1/ai/settings`.
- `localAiProviderClient.saveLocalAiSettings()` calls `/api/v1/ai/settings` with `PUT`.
- `localAiStreamClient.streamLocalPregameAi()` calls `/api/v1/ai/pregame/stream` without `Authorization`.
- `localAiStreamClient.streamLocalPostgameAi()` calls `/api/v1/ai/postgame/stream` without `Authorization`.
- `localCostClient.getLocalCostSummary()` calls `/api/v1/costs/summary`.

- [x] **Step 2: Run failing tests**

```powershell
cd rankpeek-frontend
node --test src/renderer/services/localAiProviderClient.test.ts
node --test src/renderer/services/localAiStreamClient.test.ts
node --test src/renderer/services/localCostClient.test.ts
```

Expected: FAIL because clients do not exist.

- [x] **Step 3: Implement provider client**

`localAiProviderClient.ts` exports:

```ts
export interface LocalAiSettings {
  enabled: boolean
  providerId: string
  baseUrl: string
  model: string
  apiKeySaved: boolean
  apiKeyMasked?: string | null
  temperature: number
  maxTokens: number
  pricing?: LocalAiPricing | null
}

export interface SaveLocalAiSettingsRequest extends Omit<LocalAiSettings, 'apiKeySaved' | 'apiKeyMasked'> {
  apiKey?: string
  saveApiKey: boolean
}
```

Use `RANKPEEK_LOCAL_SERVICE_BASE_URL` and `parseLocalJson`.

- [x] **Step 4: Implement shared stream client**

`localAiStreamClient.ts` must contain the SSE/NDJSON parser currently duplicated in `gamingAiServerStream.ts` and `postgameAiServerStream.ts`. Export two functions:

```ts
export function streamLocalPregameAi(...)
export function streamLocalPostgameAi(...)
```

Both functions must omit auth headers and surface provider configuration errors with this user-facing message:

```text
请先在设置里配置 AI 服务商和 API Key。
```

- [x] **Step 5: Redirect existing AI service exports**

Keep existing public function names to reduce component churn:

- `streamGamingAiAnalysis()` delegates to `streamLocalPregameAi()`.
- `streamPostgameAiAnalysis()` delegates to `streamLocalPostgameAi()`.
- `generateCoachSummaryReport()` calls `/api/v1/ai/coach-summary` without auth.

- [x] **Step 6: Replace settings account UI**

In `SettingsView.vue`, remove:

- login form
- register form
- email code
- password reset
- logout button
- RankPeek account copy

Add:

- provider select
- base URL input
- model input/select
- API key password input
- save key checkbox
- temperature input
- max tokens input
- pricing preset/custom pricing controls
- "test connection" button
- masked saved key display

- [x] **Step 7: Replace AI analysis billing UI**

In `AiAnalysisView.vue`, remove account and credits sections. Add:

- local provider status
- recent AI runs
- cost summary for today/current month
- manual cost entry shortcut

- [x] **Step 8: Run frontend AI tests**

```powershell
cd rankpeek-frontend
node --test src/renderer/services/localAiProviderClient.test.ts
node --test src/renderer/services/localAiStreamClient.test.ts
node --test src/renderer/services/localCostClient.test.ts
node --test src/renderer/services/gamingAiServerStream.test.ts
node --test src/renderer/services/postgameAiServerStream.test.ts
node --test src/renderer/services/coachSummaryAiClient.test.ts
node --test src/renderer/views/SettingsView.test.ts
node --test src/renderer/views/AiAnalysisView.test.ts
```

Expected: PASS.

- [x] **Step 9: Commit**

```powershell
git add rankpeek-frontend/src/renderer/services rankpeek-frontend/src/renderer/views/SettingsView.vue rankpeek-frontend/src/renderer/views/SettingsView.test.ts rankpeek-frontend/src/renderer/views/AiAnalysisView.vue rankpeek-frontend/src/renderer/views/AiAnalysisView.test.ts
git commit -m "feat(frontend): configure and use local AI providers"
```

---

## Task 7: Remove Auth And Credits From Desktop Frontend

**Files:**
- Delete: `rankpeek-frontend/src/renderer/services/rankpeekAuthClient.ts`
- Delete: `rankpeek-frontend/src/renderer/services/rankpeekAuthClient.test.ts`
- Delete: `rankpeek-frontend/src/renderer/services/rankpeekCreditsClient.ts`
- Delete: `rankpeek-frontend/src/renderer/services/rankpeekCreditsClient.test.ts`
- Modify: all remaining files found by `rg -n "rankpeekAuth|rankpeekCredits|RankPeek account|credits|login|register" rankpeek-frontend/src/renderer`

- [ ] **Step 1: Search remaining auth/credit references**

Run:

```powershell
rg -n "rankpeekAuth|rankpeekCredits|RANKPEEK_AUTH|RANKPEEK_CREDITS|RankPeek account|refreshStoredRankPeekAuthSession|getStoredRankPeekAuthSession" rankpeek-frontend/src/renderer
```

Expected before deletion: only planned files from Tasks 6 and 7 appear.

- [ ] **Step 2: Delete auth and credits clients**

Remove the four files listed above.

- [ ] **Step 3: Remove stale tests and copy**

Update tests that asserted auth/credit UI. Replace with assertions that no renderer file imports `rankpeekAuthClient` or `rankpeekCreditsClient`.

Add this assertion to a frontend guard test:

```ts
assert.doesNotMatch(source, /rankpeekAuthClient|rankpeekCreditsClient|api\.rankpeek\.cn/)
```

- [ ] **Step 4: Run frontend guard search**

```powershell
rg -n "rankpeekAuth|rankpeekCredits|api.rankpeek.cn|https://api.rankpeek.cn" rankpeek-frontend/src rankpeek-frontend/package.json
```

Expected: no output.

- [ ] **Step 5: Run frontend tests and build**

```powershell
cd rankpeek-frontend
node --test src/renderer/services/*.test.ts
npm run build:renderer
```

Expected: PASS. Vite may still print existing chunk warnings; build must exit 0.

- [ ] **Step 6: Commit**

```powershell
git add rankpeek-frontend/src/renderer
git commit -m "refactor(frontend): remove account and credits flows"
```

---

## Task 8: Migrate OP.GG Data To Local Backend

**Files:**
- Modify: `rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/RealOpggSourceClient.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggSourceProperties.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggSourceException.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggSourceClient.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionStats.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionService.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionPositionStats.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionListQuery.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionListProvider.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionListItem.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionList.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionDetailQuery.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionDetailProvider.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionDetail.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionCounter.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionController.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggChampionCacheRepository.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggCacheProperties.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggCacheCleanupScheduler.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/opgg/OpggBuildOption.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/opgg/OpggChampionControllerTest.java`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/opgg/OpggChampionCacheRepositoryTest.java`
- Reference: `rankpeek-server/src/main/java/io/rankpeek/server/opgg/*`

- [ ] **Step 1: Write cache repository tests**

`OpggChampionCacheRepositoryTest` must verify:

- cache miss returns empty
- saving champion list can be read by mode/region/tier
- saving champion detail can be read by champion/mode/region/tier/position
- expired cache is ignored

- [ ] **Step 2: Run failing tests**

```powershell
cd rankpeek-backend
mvn -Dtest=OpggChampionCacheRepositoryTest,OpggChampionControllerTest test
```

Expected: FAIL because local OP.GG package does not exist.

- [ ] **Step 3: Add OP.GG cache tables**

Add:

```java
jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS opgg_champion_list_cache (
            cache_key VARCHAR(255) PRIMARY KEY,
            mode VARCHAR(64),
            region VARCHAR(64),
            tier VARCHAR(128),
            raw_json CLOB,
            fetched_at BIGINT,
            expires_at BIGINT
        )
        """);

jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS opgg_champion_detail_cache (
            cache_key VARCHAR(255) PRIMARY KEY,
            champion_id INT,
            mode VARCHAR(64),
            region VARCHAR(64),
            tier VARCHAR(128),
            position VARCHAR(64),
            raw_json CLOB,
            fetched_at BIGINT,
            expires_at BIGINT
        )
        """);
```

- [ ] **Step 4: Copy and adapt server OP.GG classes**

Copy the server OP.GG classes listed in Files into `io.rankpeek.opgg`. Replace package names and imports. Replace server `ApiResponse` with local `io.rankpeek.model.ApiResponse`.

Controller mappings become:

```java
@RequestMapping("/api/v1/opgg")
```

- [ ] **Step 5: Add local throttling behavior**

`OpggChampionService` must:

- Return fresh cache before making a network call.
- Fetch and store when cache is missing or expired.
- Return stale cache when the network call fails and stale data exists.
- Throw a user-facing error only when no usable cache exists.

- [ ] **Step 6: Update frontend OP.GG endpoint expectations**

Update `rankpeekServerClient.test.ts` so:

```text
/api/v1/opgg/champions
/api/v1/opgg/champions/{championId}/detail
```

are expected.

- [ ] **Step 7: Run OP.GG tests**

```powershell
cd rankpeek-backend
mvn -Dtest=OpggChampionCacheRepositoryTest,OpggChampionControllerTest test
cd ../rankpeek-frontend
node --test src/renderer/services/rankpeekServerClient.test.ts
node --test src/renderer/views/OpggWindowView.test.ts
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java rankpeek-backend/src/main/java/io/rankpeek/opgg rankpeek-backend/src/test/java/io/rankpeek/opgg rankpeek-frontend/src/renderer/services/rankpeekServerClient.ts rankpeek-frontend/src/renderer/services/rankpeekServerClient.test.ts rankpeek-frontend/src/renderer/views/OpggWindowView.vue rankpeek-frontend/src/renderer/views/OpggWindowView.test.ts
git commit -m "feat(backend): serve OP.GG data from local backend"
```

---

## Task 9: Migrate CN Meta, Patch, LPL, And Prompt Context Data

**Files:**
- Modify: `rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/CnMetaRepository.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/CnMetaController.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/CnChampionMeta.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/CnMetaRoles.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/CnMetaService.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/RealCnMetaSourceParser.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/RealCnMetaSourceClient.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/MockCnMetaSourceClient.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSyncService.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSyncScheduler.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSyncResult.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSyncRepository.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSyncProperties.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSyncJob.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSyncController.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSourcePayload.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSourceException.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSourceDocument.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaSourceClient.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/cnmeta/sync/CnMetaChampionStatRow.java`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/patch/*`
- Create: `rankpeek-backend/src/main/java/io/rankpeek/esports/*`
- Create: `rankpeek-backend/src/test/java/io/rankpeek/cnmeta/*Test.java`
- Reference: `rankpeek-server/src/main/java/io/rankpeek/server/cnmeta/*`
- Reference: `rankpeek-server/src/main/java/io/rankpeek/server/patch/*`
- Reference: `rankpeek-server/src/main/java/io/rankpeek/server/esports/*`

- [ ] **Step 1: Write CN meta tests**

Tests must assert:

- latest champion meta returns the newest patch for a champion/tier
- champion meta list returns all tier scopes for a champion
- sync job records status, row count, started time, finished time, and error message
- failed sync keeps previous usable data

- [ ] **Step 2: Run failing tests**

```powershell
cd rankpeek-backend
mvn -Dtest=CnMeta*Test test
```

Expected: FAIL because local CN meta package does not exist.

- [ ] **Step 3: Add local CN meta tables**

Add tables equivalent to the server migrations used by CN meta, patch, and LPL data. Required local tables:

```text
cn_champion_meta
cn_meta_sync_jobs
patch_versions
patch_changes
lpl_champion_usage
playstyle_cards
```

Each table must have `updated_at BIGINT`. Sync job rows must have `status`, `started_at`, `finished_at`, `row_count`, and `error_message`.

- [ ] **Step 4: Copy and adapt CN meta, patch, and LPL classes**

Copy the referenced server classes into local backend packages. Replace:

- package `io.rankpeek.server.*` with `io.rankpeek.*`
- server `ApiResponse` with local `io.rankpeek.model.ApiResponse`
- server admin-only sync routes with local developer/maintenance routes

Controller mappings become:

```text
/api/v1/cn-meta
/api/v1/cn-meta/sync
/api/v1/patch
/api/v1/esports/lpl
```

- [ ] **Step 5: Wire prompt context to local data**

When Task 4 AI prompt context needs CN meta, patch notes, LPL notes, or playstyle cards, make `LocalAiAnalysisService` read from local repositories. If data is missing, the prompt should include a compact "local data unavailable" note instead of failing the AI request.

- [ ] **Step 6: Run tests**

```powershell
cd rankpeek-backend
mvn -Dtest=CnMeta*Test,LocalAiControllerTest test
cd ../rankpeek-frontend
node --test src/renderer/services/rankpeekServerClient.test.ts
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add rankpeek-backend/src/main/java/io/rankpeek/cache/LocalCacheSchemaInitializer.java rankpeek-backend/src/main/java/io/rankpeek/cnmeta rankpeek-backend/src/main/java/io/rankpeek/patch rankpeek-backend/src/main/java/io/rankpeek/esports rankpeek-backend/src/test/java/io/rankpeek/cnmeta rankpeek-frontend/src/renderer/services/rankpeekServerClient.ts rankpeek-frontend/src/renderer/services/rankpeekServerClient.test.ts
git commit -m "feat(backend): serve CN meta and prompt context locally"
```

---

## Task 10: Update README And Build Positioning

**Files:**
- Modify: `README.md`
- Modify: `README.zh-CN.md`
- Modify: `rankpeek-server/README.md`
- Modify: `build.bat`
- Modify: `scripts/check-no-automation.mjs` if cloud URLs need a guard

- [ ] **Step 1: Update root README**

Change the product description from cloud-backed account product to local-first desktop product:

```text
RankPeek is a Windows desktop companion for League of Legends. It reads the local League Client through a local backend, keeps local caches, and lets users configure their own AI provider for analysis.
```

Remove RankPeek account, credits, and cloud server requirements from normal desktop development.

- [ ] **Step 2: Update cloud server README**

Mark `rankpeek-server` as reference/legacy cloud service:

```text
rankpeek-server is no longer required for the packaged desktop app. It remains in the repository as reference code for AI prompts, OP.GG data, CN meta sync, and previous admin tooling.
```

- [ ] **Step 3: Add cloud URL guard**

Extend `scripts/check-no-automation.mjs` or create `scripts/check-no-cloud-server.mjs` to fail when shipping frontend code contains:

```text
https://api.rankpeek.cn
RANKPEEK_SERVER_BASE_URL
rankpeekAuthClient
rankpeekCreditsClient
```

Allow references inside `rankpeek-server`, docs, and this plan.

- [ ] **Step 4: Run docs and guard checks**

```powershell
node scripts/check-no-automation.mjs
node scripts/check-no-cloud-server.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add README.md README.zh-CN.md rankpeek-server/README.md scripts
git commit -m "docs: document local-first desktop architecture"
```

---

## Task 11: End-To-End Verification

**Files:**
- No planned code files. This task verifies the integrated migration.

- [ ] **Step 1: Run backend tests**

```powershell
cd rankpeek-backend
mvn test
```

Expected: PASS.

- [ ] **Step 2: Run server reference tests**

```powershell
cd rankpeek-server
mvn test
```

Expected: PASS until a later branch intentionally removes or archives server code.

- [ ] **Step 3: Run frontend targeted tests**

```powershell
cd rankpeek-frontend
node --test src/renderer/services/*.test.ts
node --test src/renderer/views/*.test.ts
node --test src/renderer/components/**/*.test.ts
```

Expected: PASS. If PowerShell glob expansion does not match nested component tests, run the failing subset by exact file paths from `rg --files rankpeek-frontend/src/renderer -g "*.test.ts"`.

- [ ] **Step 4: Run frontend build**

```powershell
cd rankpeek-frontend
npm run build:renderer
```

Expected: PASS. Existing Vite chunk warnings are acceptable only if exit code is 0 and no new cloud/auth import errors appear.

- [ ] **Step 5: Run guards**

```powershell
node scripts/check-no-automation.mjs
node scripts/check-no-cloud-server.mjs
```

Expected: PASS.

- [ ] **Step 6: Manual desktop smoke test**

Start local backend:

```powershell
.\scripts\dev-backend.bat
```

Start Electron:

```powershell
cd rankpeek-frontend
npm run electron:dev
```

Verify:

- Settings can save and reload AI provider settings.
- AI connection test succeeds with a user-provided key.
- Pregame AI stream works without login.
- Postgame AI stream works without login.
- Coach summary works without login.
- AI usage appears in local run history.
- Cost summary records AI cost.
- Manual one-time/monthly/yearly costs can be added and summarized.
- OP.GG champion list and detail load from local backend.
- CN meta latest endpoint returns data or a clear local-data-unavailable state.
- No UI asks for registration, login, email verification, password reset, recharge, or credits.

- [ ] **Step 7: Commit final integration fixes**

```powershell
git add rankpeek-backend rankpeek-frontend README.md README.zh-CN.md rankpeek-server/README.md scripts
git commit -m "chore: verify local-first migration"
```

---

## Migration Risks And Controls

- OP.GG may change response shape or block requests. Control: cache successful responses, return stale cache on fetch failure, and show stale timestamp in UI.
- Some free AI providers omit token usage. Control: store run with `usageUnknown=true`, display unknown cost, and allow user-entered pricing only when usage exists.
- Some OpenAI-compatible providers do not support `stream_options.include_usage`. Control: request it when configured, but tolerate missing usage.
- API keys are user-owned secrets. Control: never send them to RankPeek cloud, never return raw keys to renderer after save, and mask key display.
- Local database corruption can happen. Control: reuse existing H2 recovery pattern and make AI/cost/OP.GG tables part of the same recovery flow.
- Removing auth touches many UI tests. Control: do frontend migration after local backend AI endpoints are working, so UI has a replacement state.
- `rankpeek-server` and `rankpeek-backend` have overlapping packages after copy/adaptation. Control: keep package names distinct: `io.rankpeek.server.*` for cloud reference, `io.rankpeek.*` for local backend.

## Completion Criteria

- Packaged desktop app has no dependency on `https://api.rankpeek.cn`.
- No renderer UI offers registration, login, password reset, recharge, or credit balance.
- AI supports at least DeepSeek plus custom OpenAI-compatible provider configuration.
- AI pregame, postgame, and coach summary flows work through `rankpeek-backend`.
- AI run history and token usage are stored locally.
- Cost ledger includes AI costs and manual one-time/monthly/yearly costs.
- OP.GG list/detail data comes from local backend cache/proxy.
- CN meta and prompt-context data come from local backend storage/sync.
- `rankpeek-backend mvn test`, `rankpeek-frontend npm run build:renderer`, and guard scripts pass.
