# Cloudflare Announcement Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a token-protected Cloudflare admin page and API for publishing RankPeek announcements into D1.

**Architecture:** The public desktop client keeps using `GET /app/announcements`. The Worker adds a public `/admin` HTML page and protected `/admin/announcements` JSON APIs guarded by `Authorization: Bearer <ADMIN_TOKEN>`. D1 remains the source of truth through the existing `announcements` table.

**Tech Stack:** Cloudflare Workers module syntax, D1, plain HTML/CSS/JS admin page, Node test runner.

---

### Task 1: Admin API Tests

**Files:**
- Modify: `rankpeek-cloudflare/test/handlers.test.mjs`

- [x] Add tests that protected admin announcement endpoints reject missing or wrong bearer tokens.
- [x] Add tests that `POST /admin/announcements` validates payloads and inserts normalized rows.
- [x] Add tests that `GET /admin/announcements` lists stored announcements.
- [x] Add tests that `PATCH /admin/announcements/:id` updates announcement fields including `enabled`.

Run: `npm test` from `rankpeek-cloudflare`.
Expected before implementation: fail because admin routes do not exist.

### Task 2: Worker Admin Routes

**Files:**
- Modify: `rankpeek-cloudflare/src/handlers.mjs`
- Create: `rankpeek-cloudflare/src/admin-page.mjs`

- [ ] Serve `/admin` as a small browser admin app.
- [ ] Implement token parsing with `Authorization: Bearer <ADMIN_TOKEN>`.
- [ ] Implement `GET /admin/announcements`.
- [ ] Implement `POST /admin/announcements` with explicit validation and D1 insert.
- [ ] Implement `PATCH /admin/announcements/:id` with explicit validation and D1 update.

Run: `npm test` from `rankpeek-cloudflare`.
Expected after implementation: all Worker tests pass.

### Task 3: Final Verification

**Files:**
- Existing Worker files and tests.

- [ ] Run `npm test` from `rankpeek-cloudflare`.
- [ ] Review `git diff -- rankpeek-cloudflare docs/superpowers/plans/2026-06-03-cloudflare-announcement-admin.md`.
- [ ] Report required deployment secret: `wrangler secret put ADMIN_TOKEN`.
