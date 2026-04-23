# RankPeek

[简体中文说明](README.zh-CN.md)

RankPeek is a desktop scouting tool for League of Legends that helps you read the lobby before the game starts.

It pulls data from the local League Client (LCU) and turns it into fast player lookup, recent match summaries, teammate and opponent tags, live session scouting, and sharp AI-assisted analysis.

## Why RankPeek

- Check a summoner's recent form without leaving the desktop app.
- Read teammate and opponent signals during champion select.
- Surface lightweight tags such as streaks, carry patterns, weak links, and champion comfort picks.
- Open match details on demand instead of waiting for every game card to pre-load.
- Generate blunt, data-backed AI analysis for a match, a player, or an entire lobby.

## Feature Highlights

### Lobby scouting

- Live player cards for the current session
- Solo queue and flex rank visibility
- Compact player tags designed for fast pre-game reading
- Clear handling for private history, empty history, and fetch errors

### Match history

- Recent matches with team rosters derived directly from match history data
- Lazy-loaded match detail panels for better page speed
- Teammate and opponent summary tags visible from the history page
- Dedicated detail modal for deeper stats and AI review

### Player profiling

- Summoner overview with recent trends
- Best partner and tough opponent highlights
- Summary tags for fast scanning
- Full tag analysis view for deeper inspection

### AI analysis

- Match-level review
- Single-player review
- Session and lobby analysis
- Verdict-first prompts with a sharper, more critical tone
- Evidence-based output tied to real match metrics

### Automation

- Auto accept
- Auto queue
- Auto pick / ban helpers
- Settings-driven toggles for repetitive client actions

## How It Works

RankPeek is a Windows desktop application built with:

- `Electron + Vue 3 + TypeScript` for the desktop UI
- `Spring Boot + Java 21` for the local backend
- `LCU HTTP + WebSocket` access for League client data
- Optional AI integration through a chat-completions compatible endpoint

The core experience is LCU-first. That means the app does not need a Riot public API key for its main scouting workflow, but it also respects the limits of what the local client can expose.

## Requirements

- Windows 10 or Windows 11
- A running League of Legends client
- Node.js 18+
- Java 21
- Maven 3.9+

For native packaging, you will also need:

- GraalVM JDK 21
- Visual Studio Build Tools with C++ support

## Quick Start

### 1. Start the backend

```powershell
cd rankpeek-backend
mvn spring-boot:run
```

The backend runs on `http://127.0.0.1:8080`.

### 2. Start the desktop app in development mode

```powershell
cd rankpeek-frontend
npm install
npm run electron:dev
```

This starts the Vite dev server and opens the Electron shell for live UI iteration.

### 3. Optional: enable AI analysis

Set these environment variables before starting the backend:

```powershell
$env:AI_API_KEY="<your-api-key>"
$env:AI_API_ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:AI_MODEL="qwen-plus"
```

Only `AI_API_KEY` is required. The endpoint and model are optional overrides.

## Build a Release

For a full Windows release build with the native backend:

```powershell
.\build.bat
```

Expected outputs:

- `rankpeek-backend/target/rankpeek-native.exe`
- `rankpeek-frontend/release/RankPeek Setup <version>.exe`
- `rankpeek-frontend/release/win-unpacked/`

If you only want to build the frontend package:

```powershell
cd rankpeek-frontend
npm install
npm run electron:build
```

## Project Layout

```text
rankpeek-frontend/   Electron + Vue desktop client
rankpeek-backend/    Spring Boot local service layer
build.bat                  One-click native Windows build script
docs/                      Design and planning notes
```

## Development Notes

- The repository is branded as `RankPeek`, while some internal folder names still use the older workspace naming.
- Match history and session analysis are optimized around summary tags first, with full details loaded only when needed.
- Private match history is handled as a first-class state instead of being treated as missing data.
- AI analysis is intentionally sharp in tone, but it should stay evidence-based and avoid abusive language.

## Known Limits

- RankPeek is LCU-only for core match and lobby data.
- If the League client does not expose certain private history details, the app can only degrade gracefully instead of bypassing that limit.
- The app is currently Windows-first.

## License

This project is released under the [MIT License](LICENSE).
