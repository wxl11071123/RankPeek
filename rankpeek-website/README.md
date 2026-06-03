# RankPeek Website

This directory contains the standalone RankPeek official website.

The source has been restored from the working production `dist` so future builds do not overwrite the live site with a blank shell. Keep `src/App.vue`, `src/styles/main.css`, and `public/assets` in sync before deploying website changes.

## Commands

```bash
npm install
npm run dev
npm run build
npm run test:build-content
```

The Vite dev server uses port `5174`. The preview server uses port `4174` unless overridden from the CLI.

`npm run test:build-content` checks the generated `dist` for key website copy and required image assets.
