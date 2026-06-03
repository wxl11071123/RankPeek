# RankPeek Cloudflare Feedback And Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a small Cloudflare-backed channel for in-app feedback and app announcements while keeping RankPeek's gameplay and AI behavior local-first.

**Architecture:** A new `rankpeek-cloudflare` Worker owns public API endpoints under `api.rankpeek.cn`. The desktop renderer calls those endpoints through a focused cloud client service; feedback is submitted from Settings without logs, and announcements are pulled on app startup and dismissed locally by announcement ID.

**Tech Stack:** Cloudflare Workers module syntax, D1, optional Cloudflare Email Sending binding, Vue 3 renderer, Electron preload metadata, Node test runner.

---

### Task 1: Worker API Contract

**Files:**
- Create: `rankpeek-cloudflare/src/handlers.mjs`
- Create: `rankpeek-cloudflare/src/index.mjs`
- Create: `rankpeek-cloudflare/test/handlers.test.mjs`
- Create: `rankpeek-cloudflare/migrations/0001_feedback_announcements.sql`
- Create: `rankpeek-cloudflare/package.json`
- Create: `rankpeek-cloudflare/wrangler.toml`

- [ ] Write failing tests for feedback validation, D1 insert, email notification fallback, and announcement filtering.
- [ ] Implement CORS, `POST /app/feedback`, `GET /app/announcements`, validation, D1 access, and optional email notification.
- [ ] Add D1 tables for `feedback_messages` and `announcements`.
- [ ] Run `node --test test/handlers.test.mjs` from `rankpeek-cloudflare`.

### Task 2: Desktop Cloud Client Service

**Files:**
- Create: `rankpeek-frontend/src/renderer/services/rankpeekCloudClient.ts`
- Create: `rankpeek-frontend/src/renderer/services/rankpeekCloudClient.test.ts`

- [ ] Write failing tests for no-log feedback payloads, stable anonymous install IDs, API error handling, and dismissed announcement storage.
- [ ] Implement feedback submission, announcement fetch, install ID persistence, and local dismiss state helpers.
- [ ] Run `node --test src/renderer/services/rankpeekCloudClient.test.ts` from `rankpeek-frontend`.

### Task 3: Settings Feedback Entry

**Files:**
- Modify: `rankpeek-frontend/src/renderer/views/SettingsView.vue`
- Modify: `rankpeek-frontend/src/renderer/views/SettingsView.test.ts`
- Modify: `rankpeek-frontend/src/renderer/i18n/locales/zh-CN.ts`
- Modify: `rankpeek-frontend/src/renderer/i18n/locales/en-US.ts`

- [ ] Write failing static tests that Settings opens an in-app feedback dialog, sends contact/message/version/platform only, and does not expose a log upload control.
- [ ] Implement the feedback button in About, a modal with contact and message fields, submit state, and localized copy.
- [ ] Run `node --test src/renderer/views/SettingsView.test.ts` from `rankpeek-frontend`.

### Task 4: App Announcement Outlet

**Files:**
- Create: `rankpeek-frontend/src/renderer/components/AppAnnouncements.vue`
- Create: `rankpeek-frontend/src/renderer/components/AppAnnouncements.test.ts`
- Modify: `rankpeek-frontend/src/renderer/App.vue`
- Modify: `rankpeek-frontend/src/renderer/App.test.ts`
- Modify: `rankpeek-frontend/src/renderer/i18n/locales/zh-CN.ts`
- Modify: `rankpeek-frontend/src/renderer/i18n/locales/en-US.ts`

- [ ] Write failing static tests that the app shell renders an announcement outlet only for non-standalone routes.
- [ ] Implement a compact dismissible announcement banner that fetches once on mount and records dismissed IDs in localStorage.
- [ ] Run `node --test src/renderer/components/AppAnnouncements.test.ts src/renderer/App.test.ts` from `rankpeek-frontend`.

### Task 5: Verification

**Files:**
- Review all files created and modified above.

- [ ] Run Cloudflare Worker tests.
- [ ] Run focused frontend tests.
- [ ] Run `npm run build` from `rankpeek-frontend`.
- [ ] Document Cloudflare setup commands for D1, Email Routing, Email Sending, and the `api.rankpeek.cn` route.
