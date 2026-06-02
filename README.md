# RankPeek

RankPeek is a desktop scouting tool for League of Legends. This repository is the public source edition focused on local client integration, match history, player tags, OP.GG helpers, local cache, and the Electron desktop shell.

## Public Scope

Included:

- Local backend for League Client / Riot client data access.
- Electron + Vue desktop frontend.
- Match history, summoner lookup, live game scouting, player tags, and automation UI.
- Local cache/database code for public features.
- OP.GG champion helper window and asset rendering utilities.

Not included:

- Proprietary AI features and prompts.
- Proprietary RP Index implementation.
- Hosted/cloud service code, billing/credit systems, production deployment secrets, and private operational docs.

The public app keeps those private modules removed instead of shipping encrypted or placeholder implementations.

## Repository Layout

```text
rankpeek-backend/      Spring Boot local backend
rankpeek-frontend/     Electron + Vue desktop app
docs/                  Public build and native-image notes
scripts/               Public maintenance scripts
```

## Requirements

- Windows 10/11
- Java 17+
- Maven 3.9+
- Node.js 20+
- npm 10+

## Development

Backend:

```bash
cd rankpeek-backend
mvn test
mvn spring-boot:run
```

Frontend:

```bash
cd rankpeek-frontend
npm install
npm run build
```

Electron development:

```bash
cd rankpeek-frontend
npm run electron:dev
```

## Packaging

The Electron package expects a backend binary at:

```text
rankpeek-backend/target/rankpeek-native.exe
```

See `docs/` for native-image build notes.

## License

See `LICENSE`.
