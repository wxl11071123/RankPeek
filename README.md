# RankPeek

[简体中文](README.zh-CN.md)

RankPeek is a Windows desktop companion for League of Legends, providing real-time match history lookup, game analysis, and data tracking.

## Features

### Real-time Game Scouting

- **Current Account**: Home page displays current account, rank, and recent form
- **Game Info**: Shows detailed teammate and opponent information during champion select
- **Auto Navigation**: Automatically switches pages based on game flow phase (lobby, matchmaking, champion select, in-game)
- **Local Persistence**: Match history, details, and OP.GG cache stored locally

### Match History

- **My Match History**: View your own historical game records
- **Summoner Lookup**: Search other players' match history
- **Match Details**: Lazy-loaded detail panels with team overview, rune/build details, and data charts
- **Filtering**: Filter matches by mode, champion, and time period

### OP.GG Data

- **Champion Tier List**: Built-in OP.GG-style champion strength rankings
- **Champion Details**: View champion win rate, pick rate, ban rate, and more
- **Multi-dimensional Filtering**: Filter by rank, mode, region, and role
- **Local Cache**: Data proxied and cached locally to avoid redundant requests

### Local Cache System

- **Match Cache**: Historical matches stored locally, reducing API requests
- **Asset Cache**: Champion portraits, item icons, and other resources cached locally
- **OP.GG Cache**: OP.GG data synchronized locally for faster loading

## Download

### Option 1: Download Installer

Visit [GitHub Releases](https://github.com/wxl11071123/rankpeek/releases) to download the latest installer.

### Option 2: Build from Source

Refer to the "Developer Guide" section below.

## System Requirements

- Windows 10 or Windows 11
- Running League of Legends client (required for LCU features)
- Node.js 18+ (required for building from source)
- Java 21 (required for building from source)
- Maven 3.9+ (required for building from source)

## Usage

1. Launch the RankPeek desktop application
2. Ensure the League of Legends client is running
3. The application will automatically detect the currently logged-in account
4. View current account status and recent matches on the home page
5. During champion select, teammate and opponent information will be displayed automatically

---

# Developer Guide

The following content is intended for developers who wish to participate in development or build the project themselves.

## Architecture

RankPeek uses a local-first architecture with all data processing done locally:

```
┌─────────────────────────────────────────────────────────────┐
│                    RankPeek Desktop                          │
│                    (Electron + Vue 3)                        │
├─────────────────────────────────────────────────────────────┤
│                    Local Backend                             │
│              (Spring Boot on port 8080)                      │
├─────────────────────────────────────────────────────────────┤
│         ┌──────────┬──────────┬──────────┬──────────┐        │
│         │   LCU    │   SGP    │  Assets  │  Cache   │        │
│         └──────────┴──────────┴──────────┴──────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### Core Components

| Component | Tech Stack | Responsibility |
|-----------|------------|----------------|
| `rankpeek-frontend` | Electron + Vue 3 + TypeScript | Desktop client UI |
| `rankpeek-backend` | Java 21 + Spring Boot | Local backend service |
| `rankpeek-cloudflare` | Cloudflare Workers | Website API (feedback, announcements) |
| `rankpeek-website` | Vue 3 + Vite | Official website |

### Backend Service Modules

The local backend (`rankpeek-backend`) provides the following services:

- **LCU Connection**: Read local League Client data
- **SGP Interface**: Fetch Riot service data
- **Asset Service**: Champion, item, rune resource management
- **Match Storage**: SQLite local database persistence
- **OP.GG Proxy**: OP.GG data request proxy and caching
- **CN Meta Data**: Chinese server meta, patch, and LPL data sync

## Project Structure

```
rankpeek-rebuild/
├── rankpeek-frontend/              # Electron + Vue desktop client
│   ├── src/
│   │   ├── main/                   # Electron main process
│   │   ├── preload/                # Electron preload scripts
│   │   └── renderer/               # Vue renderer process
│   │       ├── components/         # Vue components
│   │       ├── views/              # Page views
│   │       ├── stores/             # Pinia state management
│   │       ├── services/           # Business services
│   │       └── utils/              # Utility functions
│   └── public/                     # Static assets
│       └── game-assets/            # Game assets (local cache)
│
├── rankpeek-backend/               # Local backend service
│   └── src/main/java/com/rankpeek/
│       ├── controller/             # REST controllers
│       ├── service/                # Business services
│       ├── model/                  # Data models
│       └── config/                 # Configuration classes
│
├── rankpeek-cloudflare/            # Cloudflare Worker
│   └── src/
│
├── rankpeek-website/               # Official website
│   └── src/
│
├── docs/                           # Documentation
├── scripts/                        # Development scripts
└── build.bat                       # Windows packaging script
```

## Development Environment Setup

### Prerequisites

- Windows 10/11
- Node.js 18+
- Java 21 (GraalVM recommended)
- Maven 3.9+
- Running League of Legends client (required for testing LCU features)

### Step 1: Start Local Backend

```powershell
.\scripts\dev-backend.bat
```

This starts the backend service at `http://127.0.0.1:8080` and sets the development data directory to `%LOCALAPPDATA%\RankPeek-dev`.

### Step 2: Start Electron Frontend

```powershell
cd rankpeek-frontend
npm install
npm run electron:dev
```

The `electron:dev` command will:
1. Build Electron main/preload
2. Start Vite dev server
3. Open the desktop application window

### Step 3: Verify Development Environment

1. Ensure backend service is running on port 8080
2. Ensure League of Legends client is running (for LCU functionality testing)
3. Check if the home page displays account information in the Electron app

## Build & Deploy

### Build Frontend Bundle

```powershell
cd rankpeek-frontend
npm install
npm run build
```

### Build Electron Installer

```powershell
cd rankpeek-frontend
npm install
npm run electron:build
```

Build artifacts are located in `rankpeek-frontend/release/` directory.

### Full Windows Packaging

```powershell
.\build.bat
```

This script will:
1. Run repository guard checks
2. Build native backend binary (requires GraalVM)
3. Build Electron installer

## Testing

### Frontend Tests

```powershell
cd rankpeek-frontend
node --test src/renderer/services/*.test.ts
npm run build:renderer
```

### Backend Tests

```powershell
cd rankpeek-backend
mvn test
```

### Repository Guards

```powershell
node scripts/check-no-automation.mjs
node scripts/check-no-cloud-server.mjs
```

## Open Source Scope

This repository is the open source version of RankPeek. The following features are **not included** in their complete implementation:

- **RP Index**: A player rating system based on match data
- **AI Analysis**: Pre-game analysis, post-game review, electronic coach, and other AI features

These features involve core algorithms and commercial value. To prevent technical abuse (such as modified versions being sold), the related code is not included in the open source repository.

The open source version retains the complete:
- Desktop client framework (Electron + Vue 3)
- Local backend service architecture (Spring Boot)
- LCU/SGP data reading
- Match history lookup and display
- OP.GG data integration
- Local cache system

## Data & Privacy

- All data processing is done locally, with no cloud service dependency
- AI credentials are configured by the user and stored locally
- No personal user information is collected
- No account registration or login required

## Known Limitations

- Windows only
- LCU features require the League of Legends client to be running
- Some data depends on upstream services (Riot API, OP.GG) and may be unavailable
- OP.GG data may have delays

## License

This project is released under the [MIT License](LICENSE).
