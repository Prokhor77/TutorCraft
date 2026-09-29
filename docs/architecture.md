# TutorCraft — архитектура

> Источник требований: [`docs/SPEC.md`](SPEC.md) (ТЗ v1.0) + инфраструктурные решения команды (стек «Паши»).
> Режим продукта — **гибрид** (ADR-002): ядро LMS из ТЗ (P0) + вход через Google/Telegram и Telegram-уведомления в P0. Монетизация — только подписка школы на платформу (ADR-012), курсы для учеников бесплатны.

## 1. Карта системы

```
                        ┌──────────────── Cloudflare CDN (prod) ───────────────┐
                        │   статика Next.js, HLS-чанки видео из S3             │
                        └──────────────────────────────────────────────────────┘
 Браузер / PWA ──HTTPS──► apps/web (Next.js App Router, SSR витрины и лендингов)
                               │  rewrite /api/v1/* (same-origin → cookie + CSRF-free refresh)
                               ▼
                    services/core-api (Java 21, Spring Boot 3, модульный монолит)
                     │         │           │            │             │
               PostgreSQL   MongoDB      Redis        S3/MinIO      Kafka (outbox relay)
             (люди, деньги, (структура  (rate-limit,  (файлы,        │
              оценки, аудит) курсов,     троттлинг     видео)         │  topics: tc.*.v1
                             контент,    входа, кэш)                  ▼
                             вопросы)                 ┌────────────────────────────────┐
                                                      │ services/media-worker (Go)     │
                                                      │  video.uploaded → FFmpeg HLS   │
                                                      │  → S3 → video.processed        │
                                                      ├────────────────────────────────┤
                                                      │ services/notifier (Go)         │
                                                      │  notification.requested →      │
                                                      │  Telegram bot / SMTP / WS hub  │
                                                      └────────────────────────────────┘
```

## 2. Разделение ответственности

| Компонент | Язык | Отвечает за | Хранилища |
|---|---|---|---|
| `apps/web` | TypeScript, Next.js 15 | UI всех ролей, SSR каталога и лендингов курса (SEO), PWA | — |
| `services/core-api` | Java 21, Spring Boot 3 | Вся бизнес-логика: идентичность, права, курсы, оценивание, тесты, прогресс, биллинг, аудит, публичный REST API | PostgreSQL, MongoDB, Redis, S3 |
| `services/media-worker` | Go | Транскодирование видео в HLS (FFmpeg), загрузка чанков в S3 | S3 |
| `services/notifier` | Go | Доставка уведомлений: Telegram-бот, email (SMTP), WebSocket (реальное время) | Redis (presence) |

**Почему Go-сервисы не «владеют» бизнес-данными.** ТЗ требует модульного монолита (ARCH-01) — вся доменная логика и транзакции в одном процессе. Go-сервисы — это *воркеры* по техническим задачам (видео, доставка), они получают команды из Kafka и отвечают событиями. Это сохраняет ACID для денег/оценок и даёт масштабирование тяжёлых задач отдельно (NFR-PERF-04/05).

## 3. Модули core-api (bounded contexts, ARCH-01)

```
com.tutorcraft.core
├── shared        — ядро: ошибки (RFC 9457), tenant-контекст, пагинация, idempotency, outbox, часы, ID
├── identity      — пользователи, пароли, сессии/JWT, OAuth Google/Telegram, приглашения, сброс пароля
├── org           — tenant'ы, категории, настройки/брендинг
├── access        — роли, разрешения, can(user, permission, context)  (FR-ACL-*)
├── audit         — append-only журнал (FR-REPORT-02, NFR-SEC-09)
├── files         — метаданные файлов, pre-signed upload/download, видео-статусы
├── courses       — курсы, модули, элементы (Mongo), видимость, корзина, дублирование
├── enrollment    — записи, самозапись, ссылки-приглашения, группы
├── assessment    — задания/сдачи/отзывы, банк вопросов, тесты/попытки
├── gradebook     — категории, элементы оценивания, оценки, история, очередь проверки
├── progress      — движок условий доступа (чистые функции), выполнение, завершение курса
├── communication — форумы, уведомления, календарь
├── dashboard     — «Мои задачи», главная преподавателя (BFF-агрегации)
├── billing       — подписка школы на платформу (сроки, пробный период, режим «только чтение»)
└── integrations  — API-токены, исходящие вебхуки
```

Каждый модуль:
```
<module>/
  domain/          сущности, value objects, чистые правила (без Spring)
  application/     use cases (сервисы), порты (интерфейсы репозиториев/адаптеров), авторизация
  infrastructure/  JDBC/Mongo-репозитории, адаптеры внешних систем
  api/             REST-контроллеры, DTO, маппинг
  <Module>Api.java публичный фасад модуля для других модулей
```
Правила зависимостей проверяются ArchUnit-тестом `ModuleBoundariesTest`:
- `domain` не зависит ни от чего, кроме `shared.domain`;
- модуль A обращается к модулю B только через `B.<B>Api` или доменные события;
- контроллеры не обращаются к репозиториям напрямую.

## 4. Данные (polyglot persistence, ADR-004)

| Данные | Где | Почему |
|---|---|---|
| tenant, пользователи, роли, записи, группы | PostgreSQL | связи, уникальность, транзакции |
| сдачи, попытки, оценки, история оценок | PostgreSQL | ACID, отчёты, блокировки |
| платежи, заказы | PostgreSQL | ACID (деньги) |
| аудит, outbox, idempotency | PostgreSQL | append-only, транзакционная запись вместе с изменением |
| метаданные курса (название, категория, цена, даты) | PostgreSQL | права, каталог, join с записями |
| модули, элементы, настройки активностей, блочные документы | MongoDB | гибкая схема по `ActivityType` (DATA-04), вложенные документы |
| банк вопросов и версии вопросов | MongoDB | разные схемы по типу вопроса |
| rate-limit, троттлинг входа, кэш витрины | Redis | TTL, атомарные счётчики |
| файлы, видео (оригиналы и HLS) | S3 / MinIO | объектное хранилище |

Все бизнес-записи несут `tenant_id`; фильтрация — в репозиториях (DATA-01). Идентификаторы — UUIDv7 (сортируемые по времени).

## 5. Ключевые потоки

### 5.1 Аутентификация (FR-AUTH-01..04 + OAuth)
1. `POST /auth/login` (email+пароль) **или** `POST /auth/oauth/google` (ID-token Google Identity Services) **или** `POST /auth/oauth/telegram` (данные Telegram Login Widget, проверка HMAC-SHA256 ключом бота).
2. core-api выдаёт **access JWT** (15 мин, в теле ответа, хранится в памяти клиента) + **refresh-токен** (30 дней, httpOnly Secure SameSite=Strict cookie, path `/api/v1/auth`). Refresh ротируется при каждом использовании; повторное использование старого → отзыв всего семейства (защита от кражи).
3. Троттлинг входа — Redis (по email и IP, прогрессивная задержка).

### 5.2 Загрузка и обработка видео
1. web → `POST /files/uploads` → pre-signed PUT URL в S3 (+ `fileId`).
2. web грузит файл напрямую в S3, затем `POST /files/{id}/complete`.
3. core-api проверяет размер/сигнатуру (HEAD + первые байты), помечает `ready`; для видео пишет в outbox `tc.media.video-uploaded.v1`.
4. media-worker: скачивает, FFmpeg → HLS (360p/720p), заливает в S3 `hls/{fileId}/…`, публикует `tc.media.video-processed.v1`.
5. core-api (Kafka-консьюмер) обновляет статус видео и шлёт `notification.requested` автору → notifier пушит по WebSocket «Видео готово».

### 5.3 Уведомления (FR-NOTIF-01..04)
Модуль в core-api решает, *кому и что* (с учётом `NotificationPreference`), пишет `notifications` (in-app, с `dedupe_key` — идемпотентность) и outbox-событие `tc.notify.requested.v1` для внешних каналов. notifier доставляет в Telegram/email/WebSocket.

### 5.4 Подписка школы (ADR-012)
`POST /billing/subscription/purchases` (Idempotency-Key, право `billing.manage`) → продление `paid_until` на выбранный срок + строка `subscription_payments` + аудит. Пока провайдер только `fake` (мгновенная активация). Без активной подписки `SubscriptionApi.requireActive` запрещает создание, копирование и публикацию курсов (422 `billing.subscription_inactive`). Продажи курсов нет.

### 5.5 Outbox (ARCH-04)
Любое изменение + событие пишутся в одной транзакции PostgreSQL (`outbox`). `OutboxRelay` (scheduled, `SELECT … FOR UPDATE SKIP LOCKED`) публикует в Kafka и помечает `published_at`. Консьюмеры идемпотентны по `eventId`.

## 6. Безопасность (NFR-SEC)
- Авторизация: `AccessService.require(permission, context)` в application-слое каждого use case; объектный доступ через контекст курса/элемента; чужой tenant → 404 (AC-1).
- Ответы тестов: DTO вопросов для студента не содержат ключей до выполнения правил показа (NFR-SEC-08) — отдельный `StudentQuestionView`.
- Санитизация блочных документов на сервере (белый список блоков и марок, iframe — только домены из настройки).
- Секреты — только env; `.env.example` без значений; gitleaks в CI.
- Логи — JSON, `traceId/tenantId/userId`, маскирование email/токенов.

## 7. Развёртывание
- Локально: `docker compose up` (ARCH-08) — все зависимости + сиды (демо-tenant, преподаватель, 20 студентов, демо-курс).
- Прод: контейнеры, stateless-сервисы (NFR-PERF-04); Kubernetes — позже; CDN — Cloudflare перед web и S3 (HLS).
