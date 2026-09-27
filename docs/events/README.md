# События Kafka (контракт core-api ↔ Go-сервисы)

Все сообщения — JSON, UTF-8. Ключ сообщения — `tenantId`. Конверт:

```json
{
  "eventId": "0192f3c1-...",          // UUIDv7, идемпотентность консьюмеров
  "type": "media.video-uploaded",
  "version": 1,
  "occurredAt": "2026-09-27T18:00:00Z",
  "tenantId": "0192...",
  "payload": { }
}
```

Консьюмеры обязаны: игнорировать неизвестные поля; обрабатывать повторную доставку (at-least-once) идемпотентно по `eventId`; при неустранимой ошибке — публиковать в `<topic>.dlq`.

| Топик | Продюсер → консьюмер | payload |
|---|---|---|
| `tc.media.video-uploaded.v1` | core-api → media-worker | `{ fileId, bucket, objectKey, contentType, sizeBytes, ownerUserId }` |
| `tc.media.video-processed.v1` | media-worker → core-api | `{ fileId, status: "ready" \| "failed", hlsPrefix, masterPlaylistKey, durationSec, renditions: [{ name, height, bandwidth }], error? }` |
| `tc.notify.requested.v1` | core-api → notifier | `{ notificationId, userId, category, channels: ["email","telegram","web"], title, body, link, locale, email?, telegramChatId? }` |
| `tc.notify.delivered.v1` | notifier → core-api | `{ notificationId, channel, status: "sent" \| "failed" \| "skipped", error? }` |
| `tc.telegram.linked.v1` | notifier → core-api | `{ linkCode, chatId, telegramUserId, username }` — пользователь нажал Start в боте по deep-link |
| `tc.domain.events.v1` | core-api → (вебхуки, аналитика) | `{ name: "submission.submitted" \| "grade.published" \| "enrollment.created" \| "course.completed" \| "order.paid", data }` |

JSON-схемы — в `schemas/*.json`; Go-структуры и Java-record'ы генерируются вручную и проверяются контрактными тестами на примерах из `examples/`.

## WebSocket (notifier)

`GET wss://<host>/ws?token=<accessToken>` — notifier валидирует JWT тем же секретом (`JWT_SECRET`), держит соединения по `userId`.
Сообщения сервер → клиент:
```json
{ "type": "notification", "data": { "id": "...", "title": "...", "body": "...", "link": "/courses/..", "category": "grade_published" } }
{ "type": "counter", "data": { "name": "grading_queue", "value": 15 } }
```
Счётчики публикуются core-api в `tc.notify.requested.v1` с `channels: ["web"]` и `category: "counter"`.
