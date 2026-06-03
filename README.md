# RankPeek

[简体中文](README.zh-CN.md)

RankPeek is a Windows desktop companion for League of Legends. It reads the local League Client through a local backend, keeps local caches, and lets users configure their own AI provider for analysis.

The packaged desktop app is local-first:

- `rankpeek-frontend`: Electron + Vue 3 desktop app.
- `rankpeek-backend`: local Windows service on `127.0.0.1:8080` for LCU, SGP, assets, match history, AI, OP.GG data, CN meta data, and local cost ledgers.
- `rankpeek-cloudflare`: Cloudflare Worker API for website feedback, public announcements, and the admin announcement console.

## Current Features

### Desktop Scouting

- Current account, rank, recent form, and local service status on the home page.
- Champion-select and in-game views for teammates and opponents.
- Automatic app navigation for gameflow phases, without old queue, accept, pick, or ban automation.
- Local persistence for match records, match details, AI reports, OP.GG cache, CN meta cache, and cost records.

### Match History And RP Index

- My match history and summoner lookup views.
- Lazy-loaded match detail panels with team overview, rune/build details, charts, and timeline-backed detail tabs.
- RP Index for ranked Solo/Duo and Flex games when timeline and matchup data are available.
- Compact AI snapshots built from match facts instead of raw oversized payloads.

### Local AI Analysis

- Pregame analysis for current lobby and team context.
- Postgame review and praise mode.
- Electronic Coach analysis for recent ranked games.
- User-owned AI provider configuration in Settings: provider, base URL, model, API key, API key setup link, web-search/deep-thinking toggles, and optional user-entered pricing.
- Model choices are refreshed from the provider's `/models` endpoint after the user enters Base URL + API key; failed refreshes still allow manual model entry.
- OpenAI-compatible provider presets for DeepSeek, Qwen, MiniMax, MiMo, GLM, plus custom compatible services.
- Local run history, token usage, cache-hit/cache-miss accounting, and cost estimates.

### OP.GG And CN Meta Data

- Standalone OP.GG-style champion page inside Electron.
- Champion list and champion detail views with rank, queue, region, role, and champion filters.
- Local backend proxy/cache for OP.GG data to avoid renderer CORS and centralize throttling.
- Local backend storage/sync endpoints for CN meta, patch, LPL, and prompt-context data.

### Local Cost Ledger

- AI costs are recorded locally from provider token usage when usage is available.
- AI cost rates are optional user-entered values; blank rates are treated as unknown cost.
- No RankPeek account, registration, recharge, credit balance, or hosted billing flow is required.

## Safety Position

RankPeek is not an automation product. Old auto queue, auto accept, auto pick, and auto ban UI paths are intentionally not part of the current desktop app.

Any feature that controls the League client should be treated as high-risk and reviewed separately. Users are responsible for any account consequences caused by client automation.

## Requirements

For normal desktop development:

- Windows 10 or Windows 11
- A running League of Legends client
- Node.js 18+
- Java 21
- Maven 3.9+

For native installer packaging:

- GraalVM JDK 21
- Visual Studio Build Tools with C++ support
- A valid `GRAALVM_HOME` path in `build.bat`

For AI analysis:

- A user-provided OpenAI-compatible API key, or a provider/test mode that does not require one.

## Quick Start: Desktop Development

Normal desktop work uses only the local backend plus the Electron frontend.

### 1. Start The Local Backend

```powershell
.\scripts\dev-backend.bat
```

This starts `rankpeek-backend` on:

```text
http://127.0.0.1:8080
```

It also sets:

```text
RANKPEEK_LOCAL_DATA_ROOT=%LOCALAPPDATA%\RankPeek-dev
```

That keeps development cache separate from packaged app data.

### 2. Start Electron

```powershell
cd rankpeek-frontend
npm install
npm run electron:dev
```

`electron:dev` builds the Electron main/preload bundles, starts Vite, and opens the desktop shell. In development mode, Electron expects the local backend to already be running on port `8080`.

### 3. Configure AI

Open Settings in the desktop app and configure an AI provider. Keys are stored locally by the local backend and are not sent to RankPeek cloud services.

## Build

### Frontend Bundles

```powershell
cd rankpeek-frontend
npm install
npm run build
```

### Electron Installer

```powershell
cd rankpeek-frontend
npm install
npm run electron:build
```

The Electron package expects a native local backend at:

```text
rankpeek-backend/target/rankpeek-native.exe
```

### Full Windows Installer Script

```powershell
.\build.bat
```

This script runs repository guards, builds the native `rankpeek-backend` binary, and then runs the Electron build. It is Windows-specific and expects `GRAALVM_HOME` to be adjusted for the local machine.

## Test Commands

Frontend targeted tests:

```powershell
cd rankpeek-frontend
node --test src/renderer/services/*.test.ts
npm run build:renderer
```

Local backend tests:

```powershell
cd rankpeek-backend
mvn test
```

Repository guards:

```powershell
node scripts/check-no-automation.mjs
node scripts/check-no-cloud-server.mjs
```

## Project Layout

```text
rankpeek-frontend/              Electron + Vue desktop app
rankpeek-backend/               Local Windows service for LCU, SGP, AI, OP.GG, CN meta, costs, and cache
rankpeek-cloudflare/            Cloudflare Worker API for feedback and announcements
rankpeek-website/               Public website source
docs/                           Planning, deployment, and product notes
scripts/                        Development and repository guard scripts
build.bat                       Windows native-backend + Electron packaging helper
```

## Data And Privacy Boundaries

- The local backend reads the local League Client and match-related sources for the desktop experience.
- AI provider credentials are user-owned and configured locally.
- AI snapshots are intentionally compact and natural-language oriented to reduce cost and avoid sending raw game payloads.
- Local AI runs, token usage, OP.GG cache, CN meta cache, and cost records are stored in the local backend database.
- The packaged desktop app must not require account registration, email verification, recharge, credits, or a hosted billing service.

## Known Limits

- RankPeek is Windows-first.
- The League client must be running for LCU-backed features.
- Some history, timeline, rune/build, OP.GG, or CN meta data may be unavailable when upstream sources do not expose it.
- RP Index is limited to ranked 420/440 matches with usable timeline and complete matchup data.
- AI quality, latency, usage metadata, and cost visibility depend on the user-selected provider.

## License

This project is released under the [MIT License](LICENSE).
