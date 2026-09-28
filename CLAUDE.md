# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

TutorCraft — an LMS/online-course platform for tutors (course authoring and sales, quizzes, grading, Telegram
notifications). Docs, commit messages and the default UI locale are Russian; code identifiers are English.

Requirements live in [`docs/SPEC.md`](docs/SPEC.md); many docs reference requirement IDs (`FR-ACL-02`, `NFR-SEC-08`,
`AC-1`, …) — when changing behaviour, check the ID cited in the surrounding code/doc. Implementation status per
requirement: [`docs/ROADMAP.md`](docs/ROADMAP.md). Architecture decisions: [`docs/decisions/`](docs/decisions).

## Commands

### Whole stack
```bash
./scripts/init-env.sh        # generate .env with random local secrets (idempotent, never overwrites)
docker compose up --build    # web :3000, core-api :8080 (/api/docs), Mailpit :8025, S3 :9000, notifier WS :8090
```
`SEED_DEMO_DATA=true` (dev default) creates tenant `demo`, `teacher@demo.local`, `student01..20@demo.local` and a demo
course, all with the `SEED_DEMO_PASSWORD` from `.env`. Admin console requires `ADMIN_EMAIL`/`ADMIN_PASSWORD` from `.env`
(the account is created at core-api startup; password changes need a restart).

### core-api (Java 21, Spring Boot 3, Maven — no wrapper, needs a local `mvn`)
```bash
cd services/core-api
mvn verify -DskipITs                       # unit + ArchUnit only
mvn verify                                 # also runs *IT.java via failsafe (needs Docker for Testcontainers)
mvn test -Dtest=GradeCalculatorTest         # single unit test (surefire excludes the "integration" tag)
mvn failsafe:integration-test -Dit.test=CoursesIT   # single integration test
```

### Go workers (`services/{workerkit,media-worker,notifier}`, Go 1.24, separate modules)
```bash
cd services/notifier && go vet ./... && go test -race ./...
go test ./internal/telegram/ -run TestLinkCode      # single test
```
`media-worker` and `notifier` reach `workerkit` through `replace … => ../workerkit`, so they only build from inside
the repo. `media-worker` tests invoke a real `ffmpeg` binary.

### Web (`apps/web`, Next.js 15 / React 19 / TS strict, Node ≥ 22)
```bash
cd apps/web && npm ci
CORE_API_URL=http://localhost:8080 NEXT_PUBLIC_WS_URL=ws://localhost:8090/ws npm run dev
npm run lint && npm run typecheck && npm run format:check
npm run i18n:check       # every used key exists in BOTH messages/ru.json and messages/en.json
npm run contrast:check   # WCAG AA for ~26 token pairs, light + dark
npm test                 # vitest (jsdom) — src/**/*.test.{ts,tsx}
npx vitest run src/lib/blockdoc/doc.test.ts         # single test file
npm run test:e2e         # Playwright, requires `docker compose up` (uses Mailpit :8025)
```

CI (`.github/workflows/ci.yml`) runs exactly these, plus gitleaks, Trivy (core-api), govulncheck (Go), `npm audit`.

## Architecture

Four deployables, described in [`docs/architecture.md`](docs/architecture.md):

- **`apps/web`** — all UI, SSR storefront/landings (`/c/[tenantSlug]`), PWA. Owns no business logic.
- **`services/core-api`** — modular monolith holding *all* business logic and transactions (PostgreSQL, MongoDB, Redis,
  S3, Kafka producer/consumer).
- **`services/media-worker`** (Go) — `tc.media.video-uploaded.v1` → FFmpeg HLS → S3 → `tc.media.video-processed.v1`.
- **`services/notifier`** (Go) — `tc.notify.requested.v1` → Telegram bot / SMTP / WebSocket hub; also serves `/ws`.

The Go services are *workers*, not data owners: ACID for money/grades stays inside core-api (ARCH-01). They talk to
core-api only through Kafka envelopes documented in [`docs/events/README.md`](docs/events/README.md) (at-least-once,
idempotent by `eventId`, failures → `<topic>.dlq`).

### core-api module layout (enforced, not advisory)

```
com.tutorcraft.core.<module>/
  <Module>Api.java, *Events.java, public records/enums   ← the ONLY things other modules may import
  spi/             ports this module offers for others to implement (e.g. courses.spi.ActivityType)
  domain/          pure rules, no Spring/JDBC/HTTP
  application/     @Service use cases, repository interfaces, transactions, authorization
  infrastructure/  JdbcClient / MongoTemplate repos, adapters, @KafkaListener
  web/             @RestController + DTO records, mapping only
```

`ModuleBoundariesTest` (ArchUnit) fails the build on violations. Exceptions: `access.domain` (Permission,
AccessContext, CourseRole, TenantRole) is a shared vocabulary, and `com.tutorcraft.core.seed` (dev profile) may call
other modules' use-case services — but never their SQL or repositories.

**Bean-cycle rule:** implementations of public `*Api` interfaces and SPI adapters depend only on their own
repositories plus shared infra (`Clock`, `JsonCodec`, `AuditLog`, `OutboxPublisher`, `IdempotencyService`, `Messages`).
Keep them in separate classes (`<Module>ApiImpl`, `<X>Adapter`), not in use-case services. Use-case services may depend
on other modules' `*Api`, `AccessService`, `NotificationsApi`.

Full conventions (mandatory reading before touching core-api):
[`docs/dev/backend-conventions.md`](docs/dev/backend-conventions.md).

### Cross-cutting invariants

- **Multi-tenancy** — every business row/document carries `tenant_id`/`tenantId`; every query filters on it. An object
  from another tenant must produce **404, not 403** (AC-1), plus an audit record via `AuditLog.recordIndependently`.
- **Authorization** lives in the application layer of each use case: `access.require(Permission.X,
  AccessContext.course(courseId))`. Contexts nest `PLATFORM → TENANT → CATEGORY → COURSE → MODULE/ITEM`; the 41
  permissions are listed in [`docs/permissions.md`](docs/permissions.md), source of truth is `access.domain.Permission`.
  The web client decides UI affordances from `Course.permissions`, never from role names.
- **Outbox** (ADR-005) — data change + event are written in one PostgreSQL transaction (`outbox` table);
  `OutboxRelay` publishes to Kafka. Consumers call `ProcessedEvents.markProcessed(eventId, "<consumer>")` first.
- **IDs and time** — `Ids.newId()` (UUIDv7) and an injected `java.time.Clock`; never `Instant.now()`.
- **Persistence split** (ADR-004) — PostgreSQL for people/money/grades/audit and course metadata; MongoDB for modules,
  items, activity settings, block documents and the question bank (schema varies by `ActivityType`/question type);
  Redis for rate limits, login throttling, storefront cache; S3 for files and HLS. Mongo has no transactions, so Mongo
  operations must be idempotent.
- **Flyway migration numbers are reserved per module** (V1 shared … V15 integrations — see the table in
  backend-conventions). A new module takes the next free number; schema changes are expand/contract.
- **Errors** — RFC 9457 problems from `shared.domain` exceptions with codes `<module>.<reason>`; each code needs text in
  `src/main/resources/i18n/<module>_ru.properties`, `<module>_en.properties` **and** `<module>.properties` (ru copy, the
  fallback bundle).
- **Optimistic locking** — `version BIGINT` column, `UPDATE … WHERE version = :expected`; controllers resolve it with
  `IfMatch.resolve(header, body.version())` and the client sends `If-Match`.
- **Pagination** is keyset/cursor: `PageQuery.of(cursor, limit)` → `page.toPage(rows, sortKeyFn, idFn)`.
- **Idempotency** — `idempotency.execute(new IdempotencyScope(...), key, request, View.class, () -> …)` inside a
  `@Transactional` method; the web client sends `Idempotency-Key` for submit/finish/orders.

### Web specifics

Layout and rules are documented in [`apps/web/README.md`](apps/web/README.md) (route map, contract assumptions) and
[`apps/web/docs/design-system.md`](apps/web/docs/design-system.md). Points that are easy to get wrong:

- `src/lib/api/` is the contract boundary: zod schemas mirror [`docs/api/contract.md`](docs/api/contract.md) and every
  response is validated (mismatch → `client.invalid_response`). The client holds the access token **in memory only**
  (Zustand), uses `credentials: 'include'` for the `tc_refresh` cookie and does single-flight silent refresh on 401.
- `/api/v1/*` is proxied by a Route Handler (`src/app/api/v1/[...path]/route.ts`), **not** by `next.config` rewrites —
  rewrites are frozen at build time in standalone output, which would ignore the runtime `CORE_API_URL`.
- No authenticated SSR (ADR-003): the `(app)` layout restores the session client-side.
- Server state is TanStack Query with central `src/features/query-keys.ts`; every mutation invalidates or patches keys.
- Visual decisions live only in `src/styles/tokens.css`; `tailwind.config.ts` just maps utilities to tokens.
- All UI strings go through next-intl in `messages/{ru,en}.json` (`npm run i18n:check` enforces parity).
- Offline queue (`src/lib/offline/queue.ts`) persists submit / quiz-answer / finish operations with their idempotency
  keys and flushes on reconnect.

### Adding a new course activity type

Follow [`docs/activity-type-contract.md`](docs/activity-type-contract.md): add the value to `courses.ItemType` (the only
core change), implement `courses.spi.ActivityType` in your own module, plus `ItemStatusProvider` /
`GradingQueueSource` / `CourseDataOwner` as applicable, then add a page under `apps/web/src/features/items/<type>`.

## Deployment

Two documented targets, both driven by `.github/workflows/deploy-production.yml` (push to `main` → green `ci` →
build 4 images to GHCR → scp compose files → `pull` + `up -d` → wait for healthchecks):

- [`docs/deployment-ip.md`](docs/deployment-ip.md) — **current**: bare IP `91.149.179.186` over HTTP, bundled
  SeaweedFS (`docker-compose.ip.yml`), no SMTP. `COOKIE_SECURE=false` is mandatory here, since browsers drop
  `Secure` cookies over HTTP.
- [`docs/deployment.md`](docs/deployment.md) — domain + Cloudflare + external S3/SMTP.

Server-side prod config lives in `application-prod.yml` (Swagger/OpenAPI/`/actuator` disabled, Tomcat timeouts,
`X-Forwarded-*` trusted only from proxies) and `infra/deploy/nginx/tutorcraft-ip.conf` (rate limits, scanner
blocking, separate `:9000` server for the S3 gateway). `e2e` is a standalone workflow, not part of `ci`, so a
flaky browser test cannot block a deploy.

## Known gaps in the repo (don't be surprised)

- `docs/api/openapi.json` is still not committed. The drift check now lives in `.github/workflows/e2e.yml` and
  warns instead of failing, publishing the generated spec as the `openapi-spec` artifact — download it and commit.
- `README.md` links `design/stitch`, which does not exist in the tree.
- `script-src` still needs `'unsafe-inline'` for Next's inline bootstrap; nonce-based CSP remains a TODO.
