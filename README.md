# RankPeek

[简体中文](README.zh-CN.md)

RankPeek is a Windows desktop companion for League of Legends. It reads the local League Client, keeps a local match cache, and combines that with RankPeek cloud services for account, credits, OP.GG-style champion data, and AI analysis.

The current development model is:

- `rankpeek-frontend`: Electron + Vue 3 desktop app.
- `rankpeek-backend`: local Windows agent on `127.0.0.1:8080` for LCU, SGP, assets, match history, and local cache workflows.
- `rankpeek-server`: cloud service. The frontend uses `https://api.rankpeek.cn` by default. Do not start this locally unless you are working on cloud-server code.

## Current Features

### Desktop Scouting

- Current account, rank, recent form, and local status on the home page.
- Champion-select and in-game session views for teammates and opponents.
- Automatic app navigation for gameflow phases, without old queue/pick/ban automation features.
- Local SQLite persistence for match records, match details, AI reports, and cache hydration.

### Match History And Details

- My match history and summoner lookup views.
- Lazy-loaded match detail panels.
- Team overview, rune/build details, charts, and timeline-backed detail tabs.
- Ranked-only RP Index view when timeline and complete matchup data are available.

### RP Index

RP Index is RankPeek's timeline-based single-match performance signal for ranked Solo/Duo and Flex games.

- It uses economy, level, CS, kill participation, deaths, key objectives, and vision.
- It is only generated when the match is ranked and the required timeline/detail data is available.
- The match detail page shows the RP curve and final RP score.
- AI snapshots use compact RP facts to avoid sending unnecessary token-heavy timeline data.

### AI Analysis

AI features are served through `rankpeek-server` and require a RankPeek account when the cloud server is using the real AI provider.

- Pregame analysis for current lobby/team context.
- Postgame review.
- Postgame praise mode.
- Electronic Coach: recent 20 ranked games data analysis, focused on RP, 15-minute matchup economy, kill participation, champion/role samples, and per-match facts.
- Token usage and DeepSeek usage metadata are handled through the server path for later cost tracking.

### OP.GG Window

- Standalone OP.GG-style champion page inside Electron.
- Champion list and champion detail views.
- Rank, queue, region, role, and champion filters.
- One-time auto jump on OP.GG page entry, plus a user-champion-selection jump during champion select.
- Cloud-backed OP.GG champion data through `rankpeek-server`.

### RankPeek Account And Credits

- Login, registration email code, password reset, and session refresh.
- Credit balance and ledger endpoints.
- AI requests are authenticated and billable when real AI is enabled on the server.

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

For cloud-server development:

- Java 21
- Maven 3.9+
- PostgreSQL only for production-like server runs; tests use H2

## Quick Start: Desktop Development

Normal client work uses the local `rankpeek-backend` plus the Electron frontend. The cloud server is already configured through `https://api.rankpeek.cn`.

### 1. Start the local backend agent

```powershell
.\scripts\dev-backend.bat
```

This starts `rankpeek-backend` on `http://127.0.0.1:8080` and sets:

```text
RANKPEEK_LOCAL_DATA_ROOT=%LOCALAPPDATA%\RankPeek-dev
```

That keeps development cache separate from a packaged app's production data.

### 2. Start Electron

```powershell
cd rankpeek-frontend
npm install
npm run electron:dev
```

`electron:dev` builds the Electron main/preload bundles, starts Vite, and opens the desktop shell. In development mode, Electron expects the local backend agent to already be running on port `8080`.

## Cloud Server Development

Only start `rankpeek-server` locally when changing server endpoints, AI streaming, auth, credits, OP.GG proxying, admin tooling, or deployment code.

```powershell
cd rankpeek-server
mvn spring-boot:run
```

The default local-dev server listens on:

```text
http://localhost:18080
```

To point the frontend at a local server instead of production:

```powershell
cd rankpeek-frontend
$env:VITE_RANKPEEK_SERVER_BASE_URL = "http://localhost:18080"
npm run electron:dev
```

More server details live in [rankpeek-server/README.md](rankpeek-server/README.md).

## Build

### Frontend bundles

```powershell
cd rankpeek-frontend
npm install
npm run build
```

### Electron installer

```powershell
cd rankpeek-frontend
npm install
npm run electron:build
```

The Electron package expects a native local backend at:

```text
rankpeek-backend/target/rankpeek-native.exe
```

### Full Windows installer script

```powershell
.\build.bat
```

This script builds the native `rankpeek-backend` binary and then runs the Electron build. It is Windows-specific and currently expects `GRAALVM_HOME` to be adjusted for the local machine.

### Cloud server jar

```powershell
cd rankpeek-server
mvn -B -DskipTests package
```

CI also builds and uploads `rankpeek-server/target/rankpeek-server-0.1.0.jar`.

## Test Commands

Frontend targeted tests are plain Node test files:

```powershell
cd rankpeek-frontend
node --test src/renderer/services/rankpeekServerClient.test.ts
npm run build:renderer
```

Local backend tests:

```powershell
cd rankpeek-backend
mvn test
```

Cloud server tests:

```powershell
cd rankpeek-server
mvn test
```

Automation-removal guard:

```powershell
node scripts/check-no-automation.mjs
```

## Project Layout

```text
rankpeek-frontend/              Electron + Vue desktop app
rankpeek-backend/               Local Windows agent for LCU, SGP, assets, and cache
rankpeek-server/                Cloud server for auth, credits, AI, OP.GG data, admin, and deployment
rankpeek-server/deploy/ubuntu/  Production deployment scripts and templates
docs/                           Planning, deployment, and product notes
scripts/                        Development and repository guard scripts
build.bat                       Windows native-backend + Electron packaging helper
```

## Data And Privacy Boundaries

- The local backend talks to the local League Client and SGP-compatible match sources for the desktop experience.
- The cloud server handles RankPeek account, credits, AI requests, OP.GG champion data, and admin APIs.
- The cloud server should not receive LCU tokens, SGP tokens, raw local cache databases, or unnecessary private match payloads.
- AI input snapshots are intentionally compact and natural-language oriented to reduce cost and avoid sending raw game payloads.

## Known Limits

- RankPeek is Windows-first.
- The League client must be running for LCU-backed features.
- Some history, timeline, or rune/build data may be unavailable when Riot/LCU/SGP sources do not expose it.
- RP Index is limited to ranked 420/440 matches with usable timeline and complete matchup data.
- AI features depend on the RankPeek cloud server, account status, credits, and provider availability.

## License

This project is released under the [MIT License](LICENSE).
