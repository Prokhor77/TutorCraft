# TutorCraft Web (`apps/web`)

Next.js 15 (App Router) + React 19 + TypeScript (strict) web app for all roles: the tutor marketing
landing, SSR storefront (`/c/[tenantSlug]`, course landings), authentication and the client-rendered LMS.

## Quick start

```bash
cd apps/web
npm ci
CORE_API_URL=http://localhost:8080 NEXT_PUBLIC_WS_URL=ws://localhost:8090/ws npm run dev   # http://localhost:3000
```

Full stack (from the repo root): `cp .env.example .env && docker compose up --build`.

| Variable             | When       | Purpose                                                                                           |
| -------------------- | ---------- | ------------------------------------------------------------------------------------------------- |
| `CORE_API_URL`       | runtime    | core-api base URL. Read per request by the `/api/v1/*` proxy route and by SSR storefront fetches. |
| `NEXT_PUBLIC_WS_URL` | build time | notifier WebSocket (`?token=<access>`), also added to CSP `connect-src`.                          |

| Script                                        | Does                                                                               |
| --------------------------------------------- | ---------------------------------------------------------------------------------- |
| `npm run dev` / `build` / `start`             | Next.js dev / production build (`output: 'standalone'`) / serve                    |
| `npm run lint` · `typecheck` · `format:check` | ESLint (next/core-web-vitals + next/typescript) · `tsc --noEmit` · Prettier        |
| `npm test`                                    | Vitest unit + a11y smoke tests (jsdom, vitest-axe)                                 |
| `npm run i18n:check`                          | every used translation key exists in `messages/ru.json` **and** `messages/en.json` |
| `npm run test:e2e`                            | Playwright against the running compose stack (see below)                           |
| `npm run icons`                               | regenerates PNG icons from the SVG design (Pillow)                                 |

## Architecture (ARCH-07)

```
src/
  app/                    routes (App Router). (auth) public forms, (app) authenticated client app,
                          c/[tenantSlug] SSR storefront, checkout, join, api/v1/[...path] proxy
  lib/api/                data layer: fetch client, RFC 9457 problems, zod schemas = contract types,
                          endpoint modules per domain (auth, me, org, courses, enrollment, files,
                          assessment, gradebook, quiz, forum, billing, integrations)
  lib/blockdoc/           pure BlockDoc ⇄ RichText/HTML converters, document ops, validation
  lib/offline/            persistent retry queue (submit / quiz answers / finish)
  lib/utils/              pure helpers (deadlines, server timer, calendar grid, money, color, format)
  features/<domain>/      business logic: React Query hooks, invalidation, orchestration
  components/ui/          design-system primitives only (no business logic)
  components/<domain>/    presentational/domain components
  stores/                 Zustand: in-memory auth session, UI state (theme, counters, preview)
  styles/tokens.css       ALL visual decisions (colors, radii, type scale, spacing, shadows, motion)
messages/{ru,en}.json     all UI strings (next-intl, default ru)
```

- **Server state** — TanStack Query v5 with a central `features/query-keys.ts`; every mutation invalidates
  (or optimistically patches) the affected keys. Mutation errors are toasted globally with `Problem.title`
  unless a form maps `errors[]` to fields (`features/forms/server-errors.ts`).
- **API client** (`lib/api/client.ts`) — access token only in memory (Zustand), `credentials: 'include'`
  for the `tc_refresh` cookie, **single-flight** silent refresh on 401, `Idempotency-Key`
  (`crypto.randomUUID`) for submit/finish/orders, `If-Match` for versioned PATCHes, zod validation of every
  response (contract mismatches become `client.invalid_response`), cursor pagination helpers.
- **Auth** — the `(app)` layout restores the session via `POST /auth/refresh` and guards routes client-side
  (ADR-003: no authenticated SSR). Tokens are never persisted or logged. Logout also purges the
  service-worker content cache.
- **Authorization** — UI decisions use `Course.permissions` (`lib/access/permissions.ts`), never role names;
  tenant roles are used only as navigation hints. The server stays authoritative (FR-ACL-02).
- **Realtime** — `features/realtime`: WebSocket with exponential backoff + jitter, token refresh on auth
  close codes; `notification` → toast + invalidate; `counter` → live badges (grading queue).
- **Offline** — `lib/offline/queue.ts` persists operations in `localStorage` with their idempotency key;
  flushed on `online`, periodically and on start (AC-3, NFR-REL-05). Autosave keeps a local draft so tab
  close never loses text (UX-03).
- **PWA** — `public/manifest.webmanifest`, icons, and a hand-written `public/sw.js`: cache-first for hashed
  static assets, network-first for learning-content GETs and page navigations; auth endpoints are never cached.
- **Security headers** (`next.config.ts`) — CSP (`frame-ancestors 'none'`, `object-src`, `base-uri`,
  `form-action`), `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `X-Frame-Options`.
  Script CSP still needs `'unsafe-inline'` for Next's inline bootstrap (nonce-based CSP is a TODO).

### Design system & re-skinning

Every color is a role token (`--background`, `--surface`, `--surface-muted`, `--border`, `--text`,
`--text-muted`, `--primary(-foreground|-soft)`, `--success|warning|danger|info(-soft)`, `--focus-ring`, …)
stored as `R G B` channels in `src/styles/tokens.css`, with light/dark themes (system preference + manual
toggle via `data-theme`), plus radius, type scale, spacing, shadow and motion tokens (reduced motion
respected). `tailwind.config.ts` only maps utilities to those variables, and all screens are built from
`components/ui/*`. To apply the Google Stitch mockups later, edit `tokens.css` (and, if needed, variants in
`components/ui`) — no screen code changes. Tenant branding overrides `--primary*` at runtime (FR-ADMIN-01).
Inter (latin + cyrillic, variable) is self-hosted, so builds need no Google Fonts access.

### Block editor

`components/editor` is a lightweight Notion-like editor that stores the contract **BlockDoc** directly
(no intermediate format): headings, paragraphs with bold/italic/underline/strike/code/link, lists, quote,
code, math (KaTeX, lazy-loaded), table, image (**alt required** — server save is postponed until filled,
the local draft keeps the data), file, video (upload → HLS, or embed URL), embed, callout. `+`/`/` open a
searchable block picker; paste and drag-and-drop upload files via the presigned flow (FR-CONTENT-01/02).

## Route map

| Route                                                                      | Screen                                                                                                                                                                                                                                                   |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/`                                                                        | tutor marketing landing («Курс за 15 минут», features, pricing placeholder, CTA)                                                                                                                                                                         |
| `/login` `/register` `/forgot-password` `/reset-password` `/accept-invite` | auth (Google / Telegram buttons appear when `/auth/providers` enables them; tenant chooser on `auth.tenant_required`)                                                                                                                                    |
| `/join/[token]`                                                            | accept a course invite link                                                                                                                                                                                                                              |
| `/c/[tenantSlug]` · `/c/[tenantSlug]/[courseSlug]`                         | SSR catalog and course landing (SEO metadata, OpenGraph) with Buy / Enroll CTA                                                                                                                                                                           |
| `/checkout/fake/[orderId]` · `/checkout/return`                            | dev fake payment page, return page polling the order                                                                                                                                                                                                     |
| `/home`                                                                    | «Мои задачи» (student) / teaching dashboard (teacher) / tabs for both                                                                                                                                                                                    |
| `/courses`                                                                 | course cards, search, create dialog (title only)                                                                                                                                                                                                         |
| `/courses/[id]`                                                            | outline: sidebar with module progress, collapsible modules, status/lock reasons; teacher inline editing, DnD, `+` type picker → quick create, visibility, duplicate, delete+undo, bulk hide/show/move/shift dates, settings sheet, «Как студент» preview |
| `/courses/[id]/trash`                                                      | restore deleted items/modules                                                                                                                                                                                                                            |
| `/courses/[id]/items/[itemId]`                                             | page / file / url / folder / video / assignment / quiz / forum (teacher tabs: content, submissions, questions, attempts, discussions, settings)                                                                                                          |
| `/courses/[id]/items/[itemId]/attempts/[attemptId]` (+ `/result`)          | quiz attempt (focus mode) and results                                                                                                                                                                                                                    |
| `/courses/[id]/items/[itemId]/discussions/[discussionId]`                  | forum thread                                                                                                                                                                                                                                             |
| `/courses/[id]/question-bank`                                              | categories, filters, editor for 8 question types, versions, preview-check                                                                                                                                                                                |
| `/courses/[id]/gradebook`                                                  | gradebook with inline editing, setup sheet, formula + warnings, export, publish                                                                                                                                                                          |
| `/courses/[id]/participants`                                               | people (search, roles, bulk), enrol existing users, invite links, groups (auto), progress matrix                                                                                                                                                         |
| `/grading` · `/grading/review`                                             | unified inbox · three-panel grading with J/K, Ctrl+Enter, `?`                                                                                                                                                                                            |
| `/grades` · `/grades/[courseId]`                                           | my grades                                                                                                                                                                                                                                                |
| `/calendar`                                                                | month / week / list, iCal subscription                                                                                                                                                                                                                   |
| `/notifications` · `/settings/notifications` · `/settings/profile`         | notification center, channel matrix + Telegram link, profile/password                                                                                                                                                                                    |
| `/admin/*`                                                                 | users (+CSV import), categories (DnD), branding (live preview), audit log, orders, API tokens & webhooks                                                                                                                                                 |

## API assumptions (contract gaps)

Coded strictly against `docs/api/contract.md`; where it is silent the client assumes:

1. `TrashEntry` = `{ id, kind: 'course'|'module'|'item', title, itemType?, deletedAt }`.
2. `InviteLink` = `{ id, role, expiresAt?, maxUses?, uses?, createdAt? }`.
3. `MyGradesOverview` = `{ courses: [{ courseId, courseTitle, finalPercent, finalLabel }] }` (a bare array is also accepted).
4. `GET /items/{id}/quiz/slots` returns `{ slots, questions?: QuestionSummary[] }` (“то же + разрешённые вопросы”).
5. `POST /modules/{id}/restore` exists (the contract lists restore only for courses/items) — used by trash & undo.
6. `position` in `/items/{id}/move`, `/modules/{id}/move` and category `PATCH` is a 0-based index in the target list.
7. Quick creation sends a partial `settings` with `kind`; the server fills AC-2 defaults.
8. The fake payment provider's `confirmationUrl` points to `/checkout/fake/{orderId}`; `returnUrl` is `/checkout/return?courseId=…`.
9. Teachers can `GET /attempts/{id}` to read essay responses; essay queue ids are `attemptId:slot`.
10. WebSocket close codes 1008/4001/4401 mean “token invalid” → refresh and reconnect.
11. Logo upload uses `purpose: 'cover'` (no `logo` purpose in the contract).
12. `/invite-links/accept` requires an existing account; new students first accept an email invitation.
13. `TeacherHome.recentPosts` has no `itemId`, so posts link to the course page.
14. Storefront SSR does not resolve private file URLs (`GET /files/{id}` needs auth): description images render as alt placeholders.

## Testing

- `npm test` — API client (single-flight refresh, problem parsing, headers), BlockDoc converters/validation,
  deadline grouping, server timer, offline queue, outline DnD moves, quiz responses, question data,
  helpers, i18n parity, and **vitest-axe** a11y smoke tests on key components (color contrast is enforced
  by the token palette; jsdom cannot compute it).
- `npm run test:e2e` — `e2e/first-course.spec.ts` is the SPEC §10 «Первый курс за 15 минут» scenario
  (register → course → 2 modules → page with text & video → assignment with due date → 5-question quiz →
  invite link → student via Mailpit invitation → submits & takes quiz → teacher grades with Ctrl+Enter →
  student sees the grade → 360px check). Requires `docker compose up` (web, core-api, notifier, MinIO,
  Mailpit on :8025). Env: `E2E_BASE_URL`, `E2E_MAILPIT_URL`, `E2E_PASSWORD`.

## Docker

Multi-stage `Dockerfile` (node:22-alpine, standalone output, non-root `nextjs` user, healthcheck).
`NEXT_PUBLIC_WS_URL` is a build arg; `CORE_API_URL` is runtime. Behind a TLS-intercepting proxy pass an
extra CA with `--secret id=npm_ca,src=ca.crt`.

`/api/v1/*` is proxied by a Route Handler (`src/app/api/v1/[...path]/route.ts`) rather than `next.config`
rewrites: rewrites are frozen at build time in standalone output, so compose's runtime
`CORE_API_URL=http://core-api:8080` would otherwise be ignored.

## TODO

- Nonce-based strict CSP for scripts; front-end error reporting (NFR-OBS-04).
- P1 items not in scope: PDF annotations, rubrics, “Explain access”, nested condition groups, scales UI
  (API hooks exist), web push.
- Queue text-draft saves offline (today: submit, quiz answers and finish are queued; drafts are kept locally).
- Run the Playwright scenario in CI against the compose stack; add visual regression once Stitch mockups land.
