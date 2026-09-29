# notifier

Go worker that delivers notifications over email (SMTP), Telegram and WebSocket, and runs the Telegram linking bot
(architecture.md §5.3, ADR-002, FR-NOTIF-01/04).

```
tc.notify.requested.v1 ─► per channel: email | telegram | web ─► tc.notify.delivered.v1 (one per channel)
Telegram getUpdates "/start <code>" ─► tc.telegram.linked.v1 + reply in chat
GET /ws?token=<accessJWT> ─► WebSocket hub keyed by userId
```

## Layout

| Path | Role |
|---|---|
| `cmd/notifier` | wiring: config, consumers, HTTP server (`/ws` + health), linking bot, graceful shutdown |
| `internal/config` | env → `Config`, validated at start |
| `internal/notification` | domain, no I/O: payload validation, channels/statuses, counter body, link resolution |
| `internal/delivery` | use case: `Dispatcher` (Kafka handler) routes to `Sender`s, retries per channel, publishes outcomes |
| `internal/email` | templates (`html/template` + `text/template`), MIME builder, SMTP transport, email `Sender` |
| `internal/telegram` | Bot API client, HTML formatting, telegram `Sender`, `/start` `Linker` |
| `internal/auth` | HS256 access-token validation (same secret and claims as core-api) |
| `internal/realtime` | WebSocket hub, `/ws` handler, web `Sender` |
| `../workerkit` | shared with media-worker: envelope, validation, Kafka consumer/producer/DLQ, retry, LRU, health, config |

## Behaviour

- **Kafka.** Group `KAFKA_GROUP_ID` (default `notifier`), `NOTIFIER_CONCURRENCY` readers, offset committed after
  handling (at-least-once). Invalid envelope/payload → `tc.notify.requested.v1.dlq` with `x-error`. Validation rules
  are listed in docs/events/README.md.
- **Channels** (each retried 3× with exponential backoff; a final outcome is always published):
  - `email` — `multipart/alternative` (plain + HTML, quoted-printable, RFC 2047 subject), `From: SMTP_FROM`, templates in
    `internal/email/templates/` (table layout, inline styles, preheader), button labelled `actionLabel` or
    «Открыть»/«Open» with the absolute link (`PUBLIC_BASE_URL` + relative link) plus the same link as text under it;
    category `account` gets a service footer instead of the «notifications are enabled» one. `SMTP_PORT=465` = implicit
    TLS (SMTPS), otherwise STARTTLS whenever the server offers it (Mailpit on 1025 does not); `SMTP_USERNAME` enables AUTH PLAIN (refused over plain text by `net/smtp` except on
    localhost). 5xx replies are permanent, 4xx/network errors retried. `skipped` without `email` or without `SMTP_HOST`.
  - `telegram` — `sendMessage`, `parse_mode=HTML` (`& < > "` escaped, text truncated before escaping), inline button
    «Открыть» with the absolute link, previews disabled. `429` waits `retry_after` (up to 30 s), `5xx`/network retried,
    other `4xx` (bot blocked, chat not found) permanent. `skipped` without `telegramChatId` or bot token.
    Note: Telegram rejects `localhost` URLs in buttons, so use a public `PUBLIC_BASE_URL` with a real bot.
  - `web` — `{"type":"notification","data":{id,title,body,link,category}}` to every connection of `userId` in the
    event's tenant; `category: "counter"` → `{"type":"counter","data":{name,value}}` (body is counter JSON).
    `skipped` when the user has no open connection. A counter on `email`/`telegram` is reported `skipped`.
- **Idempotency.** In-memory LRUs (50 000 entries, 24 h TTL, per process): processed `eventId`s, and the final outcome
  per `notificationId`+`channel`, so a redelivery never sends twice on a channel that already finished — even when
  publishing the outcome failed and the whole message is retried. Not shared between replicas and lost on restart
  (documented trade-off; Redis would be the next step if duplicates across restarts matter).
- **WebSocket** `GET /ws?token=…`: JWT HS256 with `JWT_SECRET` as raw UTF-8 bytes (like core-api's `JwtService`),
  `iss=tutorcraft-core`, `typ=access`, `sub`/`tid` UUIDs, `exp` required (30 s leeway); other algorithms, including
  `none`, are rejected. Invalid token → 401 before upgrade. `Origin` must equal `WEB_ORIGIN` (403 otherwise; no
  `Origin` = non-browser client, allowed). Max 10 connections per user (close 1008). Ping every 54 s, pong timeout
  60 s, inbound frames ≤ 512 bytes and ignored. Each connection has a 32-message buffer; a full buffer disconnects
  that connection (slow consumer, close 1001) without blocking others. When the access token expires the server closes
  with **4001** — the client must reconnect with a fresh token. The token and the request URL are never logged.
- **Telegram linking** (when `TELEGRAM_BOT_TOKEN` is set and `TELEGRAM_POLLING_ENABLED=true`): long-polls
  `getUpdates` (offset kept in memory; Telegram redelivers unconfirmed updates after a restart), private chats only.
  `/start <code>` → `tc.telegram.linked.v1 {linkCode, chatId, telegramUserId, username}` with the nil tenant
  (core-api resolves the tenant by code) and the reply «Готово! Уведомления TutorCraft будут приходить сюда.»;
  plain `/start` or a malformed code → instructions. **Enable polling in exactly one replica**: Telegram answers
  409 to concurrent pollers.
- **Shutdown.** SIGTERM: readiness 503, listener closed, consumers stop fetching and finish in-flight messages within
  `SHUTDOWN_TIMEOUT`, then all WebSockets are closed (1001).
- **Logs.** JSON, IDs only (`eventId`, `tenantId`, `notificationId`, `userId`, `channel`, `status`, `updateId`); never
  titles, bodies, emails, link codes, usernames, JWTs or the bot token (transport errors are scrubbed of the URL).

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `KAFKA_BROKERS` | — (required) | comma-separated |
| `KAFKA_GROUP_ID` | `notifier` | |
| `KAFKA_TOPIC_PREFIX` | `tc` | |
| `NOTIFIER_CONCURRENCY` | `4` | consumer-group members in this process |
| `JWT_SECRET` | — (required, ≥ 32 bytes) | same value as core-api |
| `WEB_ORIGIN` | — (required) | allowed WebSocket `Origin` |
| `PUBLIC_BASE_URL` | — (required) | base for absolute links in email/Telegram |
| `SMTP_HOST` | empty = email disabled | |
| `SMTP_PORT` | `1025` | `465` → implicit TLS (SMTPS) |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | empty | AUTH only when set |
| `SMTP_FROM` | required if `SMTP_HOST` set | `TutorCraft <no-reply@example.com>` |
| `SMTP_TIMEOUT` | `15s` | per message |
| `TELEGRAM_BOT_TOKEN` | empty = telegram disabled | |
| `TELEGRAM_API_BASE_URL` | `https://api.telegram.org` | |
| `TELEGRAM_POLLING_ENABLED` | `true` | set `false` on all but one replica |
| `TELEGRAM_POLL_TIMEOUT_SEC` | `30` | long-poll timeout, 1…50 |
| `HTTP_ADDR` | `:8090` | `/ws`, `/health/live`, `/health/ready` |
| `SHUTDOWN_TIMEOUT` | `15s` | |
| `LOG_LEVEL` | `info` | |

## Build and test

```bash
cd services/notifier
export GOPROXY=direct GOSUMDB=off GOFLAGS=-mod=mod   # only GitHub is reachable in the dev sandbox
go vet ./...
go test ./...          # add -race for the race detector
go build ./cmd/notifier
```

Tests use fakes and local servers only (httptest Bot API, an in-process fake SMTP server, real WebSocket
connections through `httptest.Server`); no Kafka, SMTP or Telegram is needed.

Docker (context is `services/` because of `workerkit`): `docker compose build notifier`.
