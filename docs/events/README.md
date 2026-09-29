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

`tc.notify.requested.v1`: `channels` — только каналы, по которым это событие получателю ещё не отправлялось (идемпотентность по `(userId, dedupeKey, channel)` в core-api); `"web"` — push в открытые вкладки (in-app запись уже создана), `link` — абсолютный URL; `email` присутствует только при канале email, `telegramChatId` — только при канале telegram. Категория `account` приходит только с каналом email.

Консьюмеры обязаны: игнорировать неизвестные поля; обрабатывать повторную доставку (at-least-once) идемпотентно по `eventId`; при неустранимой ошибке — публиковать в `<topic>.dlq`.

| Топик | Продюсер → консьюмер | payload |
|---|---|---|
| `tc.media.video-uploaded.v1` | core-api → media-worker | `{ fileId, bucket, objectKey, contentType, sizeBytes, ownerUserId }` |
| `tc.media.video-processed.v1` | media-worker → core-api | `{ fileId, status: "ready" \| "failed", hlsPrefix, masterPlaylistKey, durationSec, renditions: [{ name, height, bandwidth }], error? }` |
| `tc.notify.requested.v1` | core-api → notifier | `{ notificationId, userId, category, channels: ["email","telegram","web"], title, body, link, locale, email?, telegramChatId?, actionLabel? }` |
| `tc.notify.delivered.v1` | notifier → core-api | `{ notificationId, channel, status: "sent" \| "failed" \| "skipped", error? }` |
| `tc.telegram.linked.v1` | notifier → core-api | `{ linkCode, chatId, telegramUserId, username }` — пользователь нажал Start в боте по deep-link |
| `tc.domain.events.v1` | core-api → (вебхуки, аналитика) | `{ name: "submission.submitted" \| "grade.published" \| "enrollment.created" \| "course.completed", data }` |

JSON-схемы — в `schemas/*.json`; Go-структуры и Java-record'ы генерируются вручную и проверяются контрактными тестами на примерах из `examples/`.

## WebSocket (notifier)

`GET wss://<host>/ws?token=<accessToken>` — notifier валидирует JWT тем же секретом (`JWT_SECRET`), держит соединения по `userId`.
Сообщения сервер → клиент:
```json
{ "type": "notification", "data": { "id": "...", "title": "...", "body": "...", "link": "/courses/..", "category": "grade_published" } }
{ "type": "counter", "data": { "name": "grading_queue", "value": 15 } }
```
Счётчики публикуются core-api в `tc.notify.requested.v1` с `channels: ["web"]` и `category: "counter"`;
`body` счётчика — JSON-строка `{"name":"grading_queue","value":15}` (`name` — `^[a-z][a-z0-9_]{0,63}$`, `value` — целое),
notifier пересылает её клиенту как `{"type":"counter","data":{...}}`.

Соединение: сервер шлёт ping каждые ~54 с (клиент отвечает pong автоматически), сообщения клиента игнорируются.
Проверяется `Origin` (= `WEB_ORIGIN`; запросы без `Origin`, т.е. не из браузера, допускаются). Коды закрытия:
`4001 token expired` — истёк access-токен, клиент переподключается со свежим токеном;
`1008 too many connections` — у пользователя уже 10 открытых соединений; `1001` — сервер закрывает соединение
(перезапуск или медленный клиент), клиент переподключается с backoff. Невалидный токен → HTTP 401 до апгрейда,
чужой `Origin` → HTTP 403. Доставка — только в соединения с тем же `tid`, что и `tenantId` события.

## Уточнения контракта (Go-воркеры)

Совместимы с текущими Java-record'ами (`MediaEvents`, `TelegramLinkService.TelegramLinked`), не меняют их.

**Общее.** Go-сервисы публикуют конверт версии 1 с новым `eventId` (UUIDv7), `occurredAt` — RFC 3339 UTC, ключ сообщения —
`tenantId`. Значения `type`: `media.video-processed`, `notify.delivered`, `telegram.linked`. Входящее сообщение с
`version != 1`, невалидным конвертом или payload (обязательные поля, формат UUID, перечисления) сразу уходит в
`<topic>.dlq`; временные ошибки повторяются 3 раза с экспоненциальной паузой, затем — DLQ. В DLQ пишется исходные
ключ и значение плюс заголовки `x-error` (без значений полей), `x-original-topic`, `x-original-partition`,
`x-original-offset`, `x-failed-at`. Идемпотентность по `eventId` в Go-сервисах — in-memory LRU (24 ч) на экземпляр;
окончательная защита от дублей — идемпотентные консьюмеры core-api (`ProcessedEvents`).

**`tc.media.video-uploaded.v1`.** `bucket` должен совпадать с `S3_BUCKET` воркера (иначе DLQ); `objectKey` —
относительный ключ без сегментов `..`; `contentType` — `video/*`; `sizeBytes > 0`.

**`tc.media.video-processed.v1`.**
- `ready`: `hlsPrefix = "hls/{fileId}/"` (со слешем в конце), `masterPlaylistKey = "hls/{fileId}/master.m3u8"`,
  варианты — `hls/{fileId}/{name}/index.m3u8` + `seg_NNNNN.ts` (ADR-008); `durationSec` — целое (округление);
  `renditions` — от низшего к высшему, `360p` всегда, `720p` — если высота исходника ≥ 720 (апскейла нет);
  `bandwidth` — номинальный битрейт видео + аудио, бит/с (360p: 896000, 720p: 2928000; без звука — только видео).
- `failed`: только `fileId`, `status`, `error` (≤ 500 символов, одна строка, без локальных путей). Причины: файл больше
  `MAX_VIDEO_BYTES`, объект не найден, файл не читается как видео, ошибка или таймаут FFmpeg. Инфраструктурные сбои
  (S3/Kafka недоступны) не дают `failed` — сообщение повторяется и затем уходит в DLQ.

**`tc.notify.requested.v1`.** Обязательны `notificationId`, `userId` (UUID), `category` (`^[a-z][a-z0-9_]*$`),
`channels` (непустой, без повторов, из `email|telegram|web`), `title` (≤ 200 символов). Необязательны `body`
(≤ 4000), `link` (путь приложения `/…` или абсолютный `http(s)://`; относительный дополняется `PUBLIC_BASE_URL`),
`locale` (`ru`, `en` или `xx-XX`; по умолчанию `ru`), `email`, `telegramChatId` (число), `actionLabel` (≤ 64, текст
кнопки ссылки — «Задать пароль»; без него — «Открыть»). `title`/`body` — простой текст (строки через `\n` — абзацы):
notifier сам экранирует их для HTML-письма и Telegram (parse_mode HTML). Ссылку в `body` не дублируют — письмо
показывает кнопку и запасную ссылку под ней.

**`tc.notify.delivered.v1`.** Ровно одно событие на каждый канал из `channels`. `skipped` — канал неприменим
(`error` содержит причину: нет email/чата, канал не настроен, получатель офлайн для `web`, `counter` для не-`web`);
`web` = `sent`, если сообщение принято хотя бы одним открытым соединением. `failed.error` — без адресов и тел
(для SMTP — только код ответа). Повторная доставка того же `notificationId` в канал, по которому уже есть итог,
не отправляет второй раз (FR-NOTIF-04), а повторно публикует прежний итог.

**`tc.telegram.linked.v1`.** Tenant боту неизвестен: `tenantId` конверта (и ключ сообщения) —
`00000000-0000-0000-0000-000000000000`; core-api определяет tenant и пользователя по `linkCode`. Событие
публикуется только для `/start <code>` в личном чате, где `code` — `^[A-Za-z0-9_-]{16,64}$`; `username` может
отсутствовать. После перезапуска бот может повторить событие (новый `eventId`) — core-api отбросит его, т.к. код
одноразовый.
