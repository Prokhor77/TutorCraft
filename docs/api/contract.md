# REST API v1 — контракт (источник для фронтенда и воркеров)

Машиночитаемая спецификация генерируется из кода в `docs/api/openapi.yaml` (springdoc, проверка актуальности в CI — API-02). Этот документ — человекочитаемый контракт; при расхождении правится код и документ вместе.

## Общие правила

- База: `/api/v1`. JSON, UTF-8. Даты — ISO-8601 UTC (`2026-09-27T18:00:00Z`). ID — UUID-строки.
- Авторизация: `Authorization: Bearer <accessToken>`. Tenant берётся из токена; публичные эндпоинты витрины принимают `tenantSlug` в пути.
- Ошибки — RFC 9457 `application/problem+json`:
  ```ts
  type Problem = { type: string; title: string; status: number; detail?: string;
                   code: string;               // машинный код, напр. "auth.invalid_credentials"
                   errors?: { field: string; code: string; message: string }[];
                   traceId: string;
                   requestId?: string }        // id запроса в журнале активности («код ошибки» для пользователя)
  ```
- Корреляция (журнал активности): каждый ответ несёт `X-Request-Id`. Веб-клиент отправляет `X-Client-Page` (путь страницы
  без query, секретные сегменты замаскированы) и `X-Client-Session` (случайный id вкладки) — по ним журнал показывает, где
  произошло действие, и связывает действия одной вкладки (в том числе до входа).
- Пагинация (API-04): `?cursor=&limit=` (по умолчанию 25, максимум 100). Ответ: `{ items: T[], nextCursor: string | null }` → тип `Page<T>`.
- Идемпотентность (API-05): заголовок `Idempotency-Key: <uuid>` обязателен для `POST /items/{id}/submissions/submit`, `POST /attempts/{id}/finish`, `POST /billing/subscription/purchases`. Повтор с тем же ключом возвращает сохранённый ответ.
- Оптимистичная блокировка (API-06): ресурсы с полем `version: number`; `PATCH` принимает `If-Match: "<version>"`, при конфликте — 412 `code: "conflict.version"`.
- Rate limit (API-07): заголовки `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`; при превышении 429.
- Локаль: `Accept-Language: ru|en` — для текстов ошибок и писем.

## Общие типы

```ts
type Id = string
type Instant = string
type Visibility = 'published' | 'hidden' | 'scheduled'
type CourseRole = 'teacher' | 'assistant' | 'student' | 'observer' | 'guest'
type TenantRole = 'platform_admin' | 'tenant_admin' | 'category_manager'

// Блочный документ (FR-CONTENT-01, DATA-05)
type BlockDoc = { schemaVersion: 1; blocks: Block[] }
type Block =
  | { id: string; type: 'heading'; level: 1 | 2 | 3; text: RichText }
  | { id: string; type: 'paragraph'; text: RichText }
  | { id: string; type: 'list'; ordered: boolean; items: RichText[] }
  | { id: string; type: 'quote'; text: RichText }
  | { id: string; type: 'code'; language: string; code: string }
  | { id: string; type: 'math'; latex: string }
  | { id: string; type: 'table'; rows: RichText[][] }
  | { id: string; type: 'image'; fileId: Id; alt: string; caption?: string }   // alt обязателен (NFR-A11Y-01)
  | { id: string; type: 'file'; fileId: Id; name: string }
  | { id: string; type: 'video'; fileId?: Id; embedUrl?: string }
  | { id: string; type: 'embed'; url: string }                                   // домен из белого списка
  | { id: string; type: 'callout'; tone: 'info' | 'warning' | 'success'; text: RichText }
type RichText = { text: string; marks?: ('bold'|'italic'|'code'|'strike'|'underline')[]; href?: string }[]
```

Санитизация BlockDoc на сервере — `com.tutorcraft.core.shared.content.BlockDocs.sanitize(doc, embedWhitelist, fieldPrefix)`
(все модули, принимающие BlockDoc): `schemaVersion` = 1; неизвестные блоки → 400 `invalid_block_type`, неизвестные поля
отбрасываются; `href` — только `http`/`https`/`mailto` (`invalid_href`); `embed.url` и `video.embedUrl` — `http(s)` и хост из
`TenantSettings.embedWhitelist`, точное совпадение без учёта регистра (`embed_not_allowed`); у `image` обязателен непустой `alt`
(`alt_required`); `fileId` — UUID готового файла tenant (`invalid_uuid` / `file_not_ready`); у `video` ровно один из
`fileId`/`embedUrl`; `id` блоков уникальны (`duplicate_id`). Лимиты: ≤ 2000 блоков, ≤ 1000 фрагментов в RichText, ≤ 20 000
символов во фрагменте, ≤ 1 000 000 символов текста в документе (`document_too_large`), таблица ≤ 500×50, список ≤ 1000
пунктов, `code` ≤ 100 000, `latex` ≤ 10 000. Пути ошибок: `<поле>.blocks[3].text[0].href`.

## 1. Аутентификация (`identity`)

| Метод | Путь | Тело → Ответ |
|---|---|---|
| POST | `/auth/register` | `{ email, password, firstName, lastName, schoolName? }` → `AuthResponse` · создаёт tenant (владелец = tenant_admin) — регистрация репетитора |
| POST | `/auth/login` | `{ email, password, tenantSlug? }` → `AuthResponse` + cookie `tc_refresh` |
| POST | `/auth/oauth/google` | `{ idToken, tenantSlug? }` → `AuthResponse` |
| POST | `/auth/oauth/telegram` | `{ id, first_name, last_name?, username?, photo_url?, auth_date, hash, tenantSlug? }` → `AuthResponse` |
| POST | `/auth/refresh` | (cookie) → `AuthResponse` (ротация cookie) |
| POST | `/auth/logout` | (cookie) → 204 |
| POST | `/auth/logout-all` | → 204 (отзыв всех сессий) |
| POST | `/auth/password/forgot` | `{ email, tenantSlug? }` → 202 (всегда, без раскрытия существования) |
| POST | `/auth/password/reset` | `{ token, newPassword }` → 204 |
| POST | `/auth/invitations/accept` | `{ token, password, firstName, lastName }` → `AuthResponse` |
| GET | `/auth/providers` | → `{ google: { clientId } \| null, telegram: { botUsername } \| null }` (публичный) |

```ts
type AuthResponse = { accessToken: string; expiresIn: number; user: Me }
type Me = { id: Id; email: string; firstName: string; lastName: string; avatarUrl: string | null;
            timezone: string; locale: 'ru' | 'en';
            tenant: { id: Id; slug: string; name: string; branding: Branding };
            tenantRoles: TenantRole[]; telegramLinked: boolean }
type Branding = { logoUrl: string | null; primaryColor: string | null }
```

Детали (identity):
- Все ответы с `AuthResponse` (register, login, oauth/*, refresh, invitations/accept) ставят cookie `tc_refresh` (httpOnly, SameSite=Strict, `Path=/api/v1/auth`, Secure по `COOKIE_SECURE`, Max-Age = 30 дней). `/auth/refresh` и `/auth/logout` отклоняют запрос с заголовком `Origin`, отличным от `WEB_ORIGIN` → 403 `auth.origin_mismatch`.
- Вход без `tenantSlug`: email ищется во всех школах; если пароль подошёл к нескольким аккаунтам → 409 `auth.tenant_required`, `args.tenants: { slug: string; name: string }[]` (только школы, где пароль подошёл); клиент повторяет запрос с `tenantSlug`. То же для Google/Telegram, если аккаунт найден в нескольких школах.
- Ошибки входа: 401 `auth.invalid_credentials` (не раскрывает существование email), 403 `auth.account_suspended`, 429 `auth.too_many_attempts` + заголовок `Retry-After: <сек>` (прогрессивная задержка по аккаунту, блокировка по IP).
- Повторное использование уже ротированного refresh-токена отзывает все сессии этого входа → 401 `auth.refresh_invalid`.
- `/auth/oauth/*` для выключенного провайдера → 404 `auth.provider_disabled`; неверные данные → 401 `auth.oauth_invalid` / `auth.oauth_email_unverified`; неизвестный `tenantSlug` → 404 `auth.tenant_not_found`. Без `tenantSlug` новый пользователь становится владельцем новой школы (репетитор); с `tenantSlug` — участником этой школы без ролей. Пользователь Telegram получает служебный email `tg-<id>@telegram.invalid`.
- `/auth/password/reset` и `/auth/invitations/accept` с неверным/истёкшим/использованным токеном → 422 `auth.token_invalid`. Ошибки политики пароля — 400 `validation.failed` с `errors[].code` из `password_too_short | password_too_long | password_digit_required | password_letter_required`.

## 2. Профиль и «я» (`/me`)

| GET | `/me` | → `Me` |
|---|---|---|
| PATCH | `/me` | `{ firstName?, lastName?, timezone?, locale?, avatarFileId? }` → `Me` |
| POST | `/me/password` | `{ currentPassword, newPassword }` → 204 + новая cookie `tc_refresh` (все остальные сессии отозваны; для аккаунта без пароля — Google/Telegram — `currentPassword` не требуется) |
| POST | `/me/telegram/link` | → `{ deepLink: string }` (t.me/<bot>?start=<одноразовый код, 15 мин>); бот не настроен → 404 `auth.provider_disabled` |
| GET | `/me/tasks` | → `MyTasks` (FR-DASH-01) |
| GET | `/me/teaching` | → `TeacherHome` (FR-DASH-02) |
| GET | `/me/grades` | → `MyGradesOverview` |
| GET | `/me/courses` | → `CourseCard[]` |
| GET | `/me/notifications?cursor` | → `Page<Notification>` + заголовок `X-Unread-Count` |
| POST | `/me/notifications/read` | `{ ids?: Id[]; all?: boolean }` → 204 |
| GET/PUT | `/me/notification-preferences` | `NotificationPreferences` |
| GET | `/me/calendar?from&to` | → `CalendarEvent[]` |
| POST | `/me/calendar/events` | `{ title, description?, startsAt, endsAt?, allDay?: boolean }` → 201 `CalendarEvent` (`kind: 'personal'` — заметка) |
| PATCH | `/me/calendar/events/{id}` | `{ title?, description?, startsAt?, endsAt?, allDay?, clearEnd?: boolean, clearDescription?: boolean }` → `CalendarEvent` |
| DELETE | `/me/calendar/events/{id}` | 204 |
| POST | `/me/calendar/ical-token` | → `{ url }` (перевыпуск, старый отзывается) |
| GET | `/calendar/ical/{token}.ics` | публичный iCal |
| GET | `/me/calendar/lesson-courses` | → `{ id, title }[]` — курсы, где пользователь может назначать занятия (`course.edit`) |
| GET | `/courses/{courseId}/calendar/students` | → `{ id, firstName, lastName, email }[]` — активные ученики курса (`course.edit`) |
| POST | `/courses/{courseId}/calendar/lessons` | `LessonInput` → 201 `CalendarEvent` (`kind: 'lesson'`) |
| PUT | `/courses/{courseId}/calendar/lessons/{lessonId}` | `LessonInput & { version }` + `If-Match` → `CalendarEvent` (412/409 `conflict.version`) |
| DELETE | `/courses/{courseId}/calendar/lessons/{lessonId}` | 204 |

```ts
type TaskEntry = { itemId: Id; courseId: Id; courseTitle: string; itemTitle: string;
                   itemType: ItemType; dueAt: Instant | null; status: SubmissionStatus | 'not_started' | 'in_progress' }
type MyTasks = {
  overdue: TaskEntry[]; today: TaskEntry[]; thisWeek: TaskEntry[]; later: TaskEntry[];
  recentlyGraded: { itemId: Id; courseId: Id; itemTitle: string; courseTitle: string; score: number; maxScore: number; gradedAt: Instant }[];
  continueLearning: { courseId: Id; courseTitle: string; itemId: Id; itemTitle: string; progressPercent: number }[] }
type TeacherHome = {
  toGrade: { courseId: Id; courseTitle: string; count: number }[]; toGradeTotal: number;
  upcomingDeadlines: TaskEntry[];
  recentPosts: { discussionId: Id; courseId: Id; title: string; authorName: string; createdAt: Instant }[] }
type Notification = { id: Id; type: string; title: string; body: string; link: string | null; readAt: Instant | null; createdAt: Instant }
type NotificationCategory = 'new_item' | 'deadline' | 'grade_published' | 'forum_reply' | 'announcement' | 'submission_received' | 'video_ready'
type NotificationChannel = 'web' | 'email' | 'telegram'
type NotificationPreferences = { matrix: Record<NotificationCategory, Record<NotificationChannel, boolean>> }
type CalendarEvent = { id: Id; title: string; startsAt: Instant; endsAt: Instant | null; courseId: Id | null; itemId: Id | null;
  kind: 'due' | 'open' | 'close' | 'lesson' | 'personal';
  description: string | null; allDay: boolean; courseTitle: string | null; moduleId: Id | null; moduleTitle: string | null;
  itemTitle: string | null; audience: 'course' | 'students' | null; attendeeIds: Id[]; canEdit: boolean; version: number | null }
type LessonInput = { title: string; description?: string | null; startsAt: Instant; endsAt?: Instant | null;
  moduleId?: Id | null; itemId?: Id | null; attendeeIds?: Id[] }   // пусто — всем ученикам курса
type MyGradesOverview = { courses: { courseId: Id; courseTitle: string; finalPercent: number | null; finalLabel: string | null }[] }
```

Детали (dashboard, уведомления, календарь):
- `/me/tasks`: активности (`assignment`, `quiz`, `forum`) со сроком в курсах, где пользователь — студент; только видимые студентам, не сданные (`submitted`, `submitted_late`, `graded` исключаются) и не закрытые окончательно (`closeAt` в прошлом). Группы — в часовом поясе пользователя (`Me.timezone`): `overdue` — срок прошёл; `today` — до конца текущих суток; `thisWeek` — следующие 6 календарных дней (скользящая неделя); `later` — позже. Внутри групп — по возрастанию срока. `recentlyGraded` — 5 последних опубликованных оценок; `continueLearning` — пусто, пока модуль progress не подключён.
- `/me/teaching`: `toGrade` — непроверенные работы (сдачи + эссе) по курсам, где пользователь — teacher/assistant с правом `submission.grade`, по убыванию количества; `upcomingDeadlines` — до 10 сроков в ближайшие 14 дней, `status: null`; `recentPosts` — до 10 последних постов форумов.
- `/me/courses`: курсы с активной записью; `role` — старшая роль (teacher > assistant > student > observer > guest); студенту/наблюдателю — только опубликованные курсы; `progressPercent` — только для роли student. `coverUrl` пока `null` (нужен `coverFileId` в `CourseRef`).
- `/me/notifications`: новые первыми; `type` — категория. Категория `account` (сброс пароля, приглашение) в центр уведомлений не попадает — только письмо. `/me/notifications/read`: `ids` — от 1 до 500, либо `all: true`.
- `/me/notification-preferences`: матрица по всем категориям, кроме `account` (не отключается), и всем каналам; PUT принимает полную или частичную матрицу (неизвестная категория/канал → 400). Умолчания: web — везде; email — `deadline`, `grade_published`, `announcement`; telegram — `deadline`, `grade_published`, `announcement`, `video_ready`, `new_item` (доставляется, только если Telegram привязан). `submission_received` по умолчанию только web.
- Напоминания о сроке (`deadline`) — за 24 ч и за 1 ч, только студентам, не сдавшим работу; новый элемент (`new_item`) — один раз на элемент, когда он становится видимым студентам.
- `/me/calendar`: `from < to`, диапазон ≤ 366 дней (иначе 400). События курсов — `due`/`open`/`close` активностей (преподавателю — все элементы его курсов, студенту — только видимые); `id` события курса стабилен. Личное событие чужого пользователя → 404 `calendar.event_not_found`.
- Заметки (`personal`): видит только автор; `allDay: true` — событие на весь день (клиент передаёт `startsAt` = локальная полночь), `description` ≤ 4000.
- Занятия (`lesson`, FR-DASH-03): назначает пользователь с `course.edit` в курсе. `moduleId`/`itemId` — необязательная привязка к модулю и элементу курса (элемент должен лежать в указанном модуле), иначе 400 с полем `not_in_course`; `attendeeIds` — только активные студенты курса (≤ 500), иначе 400 `not_students`. Преподавателю курса видны все занятия с `attendeeIds`; ученику — занятия всего курса (`audience: 'course'`) и назначенные ему лично, без `attendeeIds`; неопубликованный элемент ученику не показывается (`itemId`/`itemTitle` = null). `canEdit` — можно менять/удалять. Ученики получают уведомления категории `announcement`: назначено / перенесено (изменилось время) / отменено (`calendar.lesson_*`). Чужое/несуществующее занятие → 404 `calendar.lesson_not_found`.
- iCal: `url` = `${PUBLIC_BASE_URL}/api/v1/calendar/ical/<token>.ics`; в БД — только SHA-256 токена. Окно: 30 дней назад — 365 дней вперёд, время в UTC (события «весь день» — `VALUE=DATE` в часовом поясе пользователя), `SUMMARY` локализован по языку пользователя, `DESCRIPTION` — курс, модуль, элемент и текст события. Неизвестный/перевыпущенный токен → 404 `calendar.ical_not_found`.

## 3. Организация (`org`)

| GET/PATCH | `/tenant` | `TenantSettings` (FR-ADMIN-01); PATCH принимает `{ name, logoFileId, primaryColor, defaultLocale, defaultTimezone, passwordPolicy, embedWhitelist, version }` |
|---|---|---|
| GET | `/categories` | → `Category[]` (плоский список с `parentId`, `position`) |
| POST | `/categories` | `{ name, parentId? }` → `Category` |
| PATCH | `/categories/{id}` | `{ name?, parentId?, moveToParent?: boolean, position? }` (перенос применяется только при `moveToParent: true`; `parentId: null` — в корень) |
| DELETE | `/categories/{id}` | 204 (только пустая) |

```ts
type TenantSettings = { id: Id; slug: string; name: string; branding: Branding; logoFileId: Id | null; defaultLocale: 'ru'|'en';
                        defaultTimezone: string; passwordPolicy: { minLength: number; requireDigit: boolean; requireLetter: boolean };
                        embedWhitelist: string[]; version: number }
type Category = { id: Id; parentId: Id | null; name: string; position: number; courseCount: number }
```

## 4. Пользователи (`identity`, tenant_admin)

| GET | `/users?q&status&role&cursor` | `Page<UserSummary>` |
|---|---|---|
| POST | `/users` | `{ email, firstName, lastName, tenantRoles?: TenantRole[], sendInvite: boolean }` → `UserSummary` |
| GET/PATCH | `/users/{id}` | `UserSummary` / `{ firstName?, lastName?, status?: 'active'|'suspended', tenantRoles? }` |
| POST | `/users/import/preview` | multipart `file` (CSV: `email,firstName,lastName,courseShortName?,role?`) → `ImportPreview` |
| POST | `/users/import/commit` | `{ previewId }` → `{ created: number; enrolled: number; errors: ImportRowError[] }` |
| POST | `/users/{id}/invite` | 204 (повторная отправка приглашения) |

```ts
type UserSummary = { id: Id; email: string; firstName: string; lastName: string; status: 'active'|'suspended'|'invited';
                     tenantRoles: TenantRole[]; lastLoginAt: Instant | null; createdAt: Instant }
type ImportRowError = { row: number; field: string; code: string; message: string }
type ImportPreview = { previewId: Id; valid: number; invalid: number; rows: { row: number; email: string; firstName: string; lastName: string; courseShortName?: string; errors: ImportRowError[] }[] }
```

Детали (users): права — `user.view` (GET), `user.manage` (POST/PATCH/invite), `user.import` (импорт), назначение `tenantRoles` дополнительно требует `role.manage`; назначать здесь можно только `tenant_admin` (category_manager — через категории). `role` в фильтре — ключ роли уровня tenant. Созданный пользователь имеет статус `invited` до принятия приглашения (`sendInvite: false` — письмо не отправляется, его можно выслать позже `POST /users/{id}/invite`). Приостановка отзывает все сессии пользователя; себя приостановить или лишить `tenant_admin` нельзя (422 `user.cannot_suspend_self` / `user.cannot_demote_self`). Email занят → 409 `user.email_taken`.
Импорт: CSV UTF-8 (BOM допускается), ≤ 5 МБ и ≤ 5000 строк; `row` — номер строки файла (заголовок — 1). Коды ошибок строк: `required`, `invalid_email`, `duplicate_in_file`, `too_long`, `unknown_course`, `invalid_role`, `course_required`, `already_exists`. Существующий пользователь только записывается на курс. Предпросмотр живёт сутки и доступен только автору; повторный commit → 404 `user.import_preview_not_found`.

### 4.1. Ученики своей школы (`identity`, владелец школы)

Репетитор-владелец (`tenant_admin`) ведёт учеников сам, без администратора платформы. Права `member.view` (GET) и
`member.manage` (PATCH, ссылка активации); у `tenant_admin` они есть, в отличие от админских `user.*`.

| GET | `/school/members?q&status&origin&cursor` | `Page<SchoolMember>` |
|---|---|---|
| PATCH | `/school/members/{id}` | `{ status: 'active'|'suspended' }` → `SchoolMember` (блокировка/разблокировка в школе) |
| POST | `/school/members/{id}/activation-link` | → `{ activationUrl }` (новая ссылка, прежняя аннулируется) |

```ts
type AccountOrigin = 'unknown' | 'self_signup' | 'school_owner' | 'tutor_invite' | 'admin' | 'import' | 'system'
type AccountCreator = { id: Id; email: string; firstName: string; lastName: string }
type SchoolMember = { id: Id; email: string; firstName: string; lastName: string; status: 'active'|'suspended'|'invited';
                      platformBlocked: boolean; origin: AccountOrigin; createdBy: AccountCreator | null;
                      tenantRoles: TenantRole[]; lastLoginAt: Instant | null; createdAt: Instant }
```

Детали: `status` фильтра — `active | suspended | invited | blocked` (`blocked` — заблокированные платформой; `active` их
не включает). Блокировка в школе (`users.status = suspended`) отзывает сессии и закрывает вход; владельца школы
блокировать здесь нельзя (422 `user.protected`), себя — 422 `user.cannot_suspend_self`. Блокировку платформой
репетитор видит (`platformBlocked`), но снять не может. Ссылка активации — только для `invited` (иначе 422
`user.not_invited`; заблокированному платформой — 422 `user.blocked`). `origin = unknown` — аккаунты, созданные до V18.

### 4.2. Пользователи платформы (`identity`, главный администратор)

Право `platform.manage`. Работает по всем школам сразу (заголовок `X-Tenant-Id` не нужен).

| GET | `/platform/users?tenantId&q&status&origin&cursor` | `Page<PlatformUser>` (новые первыми) |
|---|---|---|
| POST | `/platform/users/{id}/block` | `{ reason?: string (≤ 500) }` → `PlatformUser` |
| POST | `/platform/users/{id}/unblock` | → `PlatformUser` |
| DELETE | `/platform/users/{id}` | 204 — полное удаление (FR-USER-05) |

```ts
type PlatformUser = { id: Id; email: string; firstName: string; lastName: string; status: 'active'|'suspended'|'invited';
                      platformBlock: { at: Instant; reason: string | null } | null; origin: AccountOrigin;
                      createdBy: AccountCreator | null; school: { id: Id; slug: string; name: string };
                      tenantRoles: TenantRole[]; lastLoginAt: Instant | null; createdAt: Instant }
```

Детали: блокировка платформой не зависит от блокировки школой, отзывает все сессии; вход → 403
`auth.account_blocked`, API-токены перестают действовать, в других модулях пользователь виден как `suspended`.
Нельзя действовать над собой (422 `user.cannot_manage_self`) и над главным администратором (422 `user.protected`).
Удаление: владельца школы — 422 `user.cannot_erase_school_owner` (только блокировка). Каждый модуль стирает данные
пользователя (SPI `identity.spi.UserDataEraser`): записи и группы, индивидуальные сдачи с файлами и отзывами, попытки
тестов, оценки с историей, прогресс, уведомления и календарь, API-токены, журнал активности; текст постов форума
стирается, посты скрываются. Учётная запись обезличивается (`deleted-<id>@deleted.invalid`, «Удалённый пользователь»)
и помечается удалённой — на неё продолжают ссылаться чужие данные: выставленные им оценки и отзывы, групповые сдачи,
заказы, созданные им ссылки. email снова свободен. Файлы сдач в S3 пока не удаляются (только ссылки на них).

## 5. Курсы и структура (`courses`)

| GET | `/courses?q&categoryId&cursor&mine` | `Page<CourseCard>` |
|---|---|---|
| POST | `/courses` | `{ title, shortName?, categoryId?, description?: BlockDoc, startsAt?, endsAt?, coverFileId? }` → `Course` (UX-02: только `title` обязателен) |
| GET | `/courses/{id}` | `Course` |
| PATCH | `/courses/{id}` | `If-Match` + частичный `Course` |
| DELETE | `/courses/{id}` | 204 (в корзину) |
| POST | `/courses/{id}/restore` | 204 |
| POST | `/courses/{id}/duplicate` | → `Course` |
| GET | `/courses/{id}/outline` | → `CourseOutline` (с учётом прав: студент получает только доступное/видимое + причины блокировки) |
| POST | `/courses/{id}/modules` | `{ title, parentId? }` → `Module` |
| PATCH | `/modules/{id}` | `{ title?, visibility?, publishAt?, conditions?, version }` → `Module` |
| DELETE | `/modules/{id}` | 204 (в корзину вместе с подмодулями и элементами) |
| POST | `/modules/{id}/restore` | 204 (восстанавливает и всё, что удалено вместе с модулем) |
| POST | `/modules/{id}/duplicate` | → `Module` (201) |
| POST | `/modules/{id}/move` | `{ position, parentId? }` → 204 |
| POST | `/modules/{id}/items` | `{ type: ItemType, title, settings?: object }` → `Item` |
| GET | `/items/{id}` | `ItemDetail` (для студента — без скрытых полей и ключей) |
| PATCH | `/items/{id}` | `{ title?, visibility?, publishAt?, settings?, content?: BlockDoc, completionRule?, conditions?, version }` |
| DELETE | `/items/{id}` | 204 |
| POST | `/items/{id}/restore` | 204 |
| POST | `/items/{id}/move` | `{ moduleId, position }` → 204 |
| POST | `/items/{id}/duplicate` | → `Item` |
| POST | `/items/{id}/complete` | 204 (ручная отметка студентом) · DELETE — снять |
| GET | `/trash?courseId` | → `TrashEntry[]` |
| GET | `/public/{tenantSlug}/courses` | публичный каталог (SSR) → `PublicCourse[]` |
| GET | `/public/{tenantSlug}/courses/{courseSlug}` | → `PublicCourse` (лендинг) |

```ts
type ItemType = 'page' | 'file' | 'url' | 'folder' | 'video' | 'assignment' | 'quiz' | 'forum'
type CourseCard = { id: Id; title: string; shortName: string | null; coverUrl: string | null; categoryId: Id | null;
                    role: CourseRole | null; progressPercent: number | null; visibility: Visibility }
type Money = { amountMinor: number; currency: string }  // только подписка (§13): 3000 USD = 30,00 $
type Course = { id: Id; title: string; shortName: string | null; slug: string; categoryId: Id | null; description: BlockDoc | null;
                coverFileId: Id | null; coverUrl: string | null; startsAt: Instant | null; endsAt: Instant | null;
                visibility: Visibility; publishAt: Instant | null;
                selfEnrol: { enabled: boolean; code: string | null; maxStudents: number | null; until: Instant | null };
                completionRule: CourseCompletionRule; groupMode: 'none' | 'visible' | 'separate';
                myRole: CourseRole | null; permissions: string[]; version: number }
type CourseCompletionRule = { requiredItemIds: Id[]; minFinalPercent: number | null }
type CourseOutline = { courseId: Id; modules: OutlineModule[] }
type OutlineModule = { id: Id; parentId: Id | null; title: string; position: number; visibility: Visibility; publishAt: Instant | null;
                       availability: Availability; items: OutlineItem[]; children: OutlineModule[]; version: number }
type OutlineItem = { id: Id; type: ItemType; title: string; position: number; visibility: Visibility; publishAt: Instant | null;
                     dueAt: Instant | null; availability: Availability;
                     completion: 'complete' | 'incomplete' | null;
                     status: SubmissionStatus | 'not_started' | 'in_progress' | null; version: number }
type Availability = { available: boolean; mode: 'show_locked' | 'hide'; reasons: string[] }   // reasons — локализованный текст (UX-07)
type Item = OutlineItem & { moduleId: Id; courseId: Id; settings: ItemSettings; content: BlockDoc | null;
                            completionRule: ItemCompletionRule; conditions: ConditionGroup | null }
type ItemDetail = Item & { permissions: string[] }
type ItemCompletionRule = { mode: 'none' | 'manual' | 'auto'; on?: ('viewed'|'submitted'|'graded'|'passed'|'posted')[] }

// Условия доступа (FR-PROG-02)
type ConditionGroup = { op: 'all' | 'any'; showWhenLocked: boolean; conditions: Condition[] }
type Condition =
  | { type: 'date'; from?: Instant; until?: Instant }
  | { type: 'completion'; itemId: Id; state: 'complete' | 'incomplete' }
  | { type: 'grade'; itemId: Id; minPercent?: number; maxPercent?: number }
  | { type: 'group'; groupId: Id }

// Настройки по типу (DATA-04, валидация на сервере)
type ItemSettings =
  | { kind: 'page' }
  | { kind: 'file'; fileId: Id | null }
  | { kind: 'url'; url: string }
  | { kind: 'folder'; fileIds: Id[] }
  | { kind: 'video'; fileId: Id | null; embedUrl: string | null; videoStatus?: 'processing' | 'ready' | 'failed'; hlsUrl?: string | null }
  | AssignmentSettings | QuizSettings | ForumSettings
type AssignmentSettings = { kind: 'assignment'; submissionType: 'file' | 'text' | 'both' | 'none'; maxScore: number;
  dueAt: Instant | null; openAt: Instant | null; closeAt: Instant | null; allowedExtensions: string[]; maxFiles: number;
  maxFileSizeMb: number; maxAttempts: number | null; groupSubmission: boolean; requireSubmitButton: boolean;
  gradeCategoryId: Id | null; autoPublishGrades: boolean }
// Умолчания AC-2: submissionType 'file', maxScore 100, dueAt null, maxFiles 5, maxFileSizeMb 50, requireSubmitButton true, autoPublishGrades true
type Module = OutlineModule   // ответ POST /courses/{id}/modules, PATCH /modules/{id}, POST /modules/{id}/duplicate
type TrashEntry = { kind: 'course' | 'module' | 'item'; id: Id; courseId: Id; title: string; itemType: ItemType | null;
                    deletedAt: Instant; purgeAt: Instant }
```

Детали (courses):
- Права: создание курса — `course.create` в категории (или tenant, если `categoryId` не задан); автор записывается `teacher`
  (method `manual`). Курс создаётся **скрытым** (`visibility: 'hidden'`), slug генерируется из названия (транслитерация,
  уникален в tenant, не меняется при переименовании). `PATCH /courses/{id}`: `course.edit`; `visibility`/`publishAt` —
  дополнительно `course.publish`; `selfEnrol` — `enrollment.manage`; `groupMode` — `group.manage`; смена `categoryId` —
  `course.create` в целевой категории.
  `Course.selfEnrol.code` отдаётся только имеющим `enrollment.manage` (иначе `null`). `shortName` занят → 409 `course.short_name_taken`.
- Цены курса нет: курсы для учеников бесплатны, платит только школа — подписка (§13.2, ADR-012). Поле `price` в PATCH игнорируется.
- Модули: один уровень вложенности (→ 422 `module.depth_exceeded`); модуль верхнего уровня создаётся `published`, подмодуль и
  новый элемент наследуют видимость родителя (AC-2). Правка структуры (модули/элементы, их видимость, корзина) — `course.edit`.
  `move`: `position` — индекс среди соседей (обрезается в допустимый диапазон), `parentId: null` — верхний уровень.
- `GET /courses?mine=true` — только курсы с активной записью; без `mine` — все курсы tenant для `tenant_admin`, курсы своих
  категорий для `category_manager`, иначе курсы с записью. Скрытые курсы учащимся (student/observer/guest) не показываются.
- Учащемуся скрытый/ещё не опубликованный курс → 403 `course.hidden`; скрытый элемент → 404 `item.not_found`; недоступный
  по условиям → 403 `item.locked`, `args.reasons: string[]`. Открытие доступного элемента учащимся фиксирует просмотр
  (выполнение «просмотрено»). Для видео `settings` дополнены `videoStatus` и `hlsUrl` (только чтение).
- Корзина: удалённое восстанавливается в течение `tutorcraft.trash.retention` (по умолчанию 30 дней), затем очищается ежедневно;
  после срока → 422 `trash.expired`. Элемент удалённого модуля → 422 `item.module_deleted`, подмодуль удалённого модуля →
  422 `module.parent_deleted`. `GET /trash` без `courseId` — удалённые курсы, которые пользователь может восстановить
  (`course.delete` на уровне tenant/категории или преподаватель курса); с `courseId` — модули/элементы курса (`course.edit`).
- Дублирование: копия встаёт сразу после оригинала с суффиксом « (копия)»/« (copy)» (по `Accept-Language`); копия курса —
  скрытая, без краткого имени и самозаписи, автор — преподаватель; ссылки на элементы в условиях доступа и правиле
  завершения переносятся на копии.
- Публичная витрина (без авторизации): только опубликованные курсы активного tenant; неизвестный tenant/курс → 404
  `course.not_found`; ответы кэшируются до 60 с и сбрасываются при изменении курсов tenant.

## 6. Записи и группы (`enrollment`)

| GET | `/courses/{id}/enrollments?q&role&groupId&cursor` | `Page<Enrollment>` |
|---|---|---|
| POST | `/courses/{id}/enrollments` | `{ userIds: Id[], role: CourseRole, startsAt?, endsAt? }` → `{ created: number }` |
| PATCH | `/enrollments/{id}` | `{ role?, status?: 'active'|'suspended'|'completed', startsAt?, endsAt? }` |
| DELETE | `/enrollments/{id}` | 204 |
| POST | `/courses/{id}/self-enrol` | `{ code? }` → `Enrollment` |
| POST | `/courses/{id}/invite-links` | `{ role: CourseRole, expiresAt?, maxUses? }` → `{ id, url }` (токен показывается один раз) |
| GET | `/courses/{id}/invite-links` | `InviteLink[]` (без токена) · DELETE `/invite-links/{id}` |
| POST | `/invite-links/accept` | `{ token }` → `{ courseId }` (требует вход; при отсутствии аккаунта — регистрация в tenant курса) |
| GET/POST | `/courses/{id}/groups` | `Group[]` / `{ name }` |
| PATCH/DELETE | `/groups/{id}` | |
| PUT | `/groups/{id}/members` | `{ userIds: Id[] }` |
| POST | `/courses/{id}/groups/auto` | `{ strategy: 'by_count' | 'by_size', value: number, prefix?: string }` → `Group[]` |

```ts
type Enrollment = { id: Id; user: { id: Id; firstName: string; lastName: string; email: string; avatarUrl: string | null };
                    role: CourseRole; status: 'active'|'suspended'|'completed'; method: 'manual'|'self'|'invite_link'|'payment'|'import'; // 'payment' — только старые записи (ADR-012)
                    startsAt: Instant | null; endsAt: Instant | null; groupIds: Id[]; lastAccessAt: Instant | null }
type Group = { id: Id; name: string; memberIds: Id[] }
type InviteLink = { id: Id; role: CourseRole; expiresAt: Instant | null; maxUses: number | null; uses: number;
                    revokedAt: Instant | null; createdAt: Instant; active: boolean }
```

Детали (enrollment):
- Права: список участников и групп — `enrollment.view`; запись, изменение, удаление, ссылки-приглашения — `enrollment.manage`;
  группы — `group.manage`. `POST /courses/{id}/enrollments` (≤ 500 пользователей tenant, иначе 400 `userIds`) → 201
  `{ created }`; повторная запись перезаписывает роль/даты и реактивирует. Доступ даёт только `status = active` и
  текущее время в `[startsAt, endsAt)`. `DELETE /enrollments/{id}` удаляет запись и членство в группах курса, сдачи и
  оценки сохраняются. Нельзя удалить/приостановить/понизить последнего активного преподавателя → 422 `enrollment.last_teacher`.
- `?q` — подстрока имени, фамилии или email; `?role` — ключ роли; `?groupId` — участники группы.
- Самозапись (`POST /courses/{id}/self-enrol`, только опубликованный курс своего tenant, иначе 404 `course.not_found`), по
  порядку проверки: выключена → `enrollment.self_enrol_disabled`; срок `until` прошёл → `enrollment.self_enrol_closed`; неверный код →
  `enrollment.invalid_code`; мест нет (активных студентов ≥ `maxStudents`) → `enrollment.course_full`. Уже активная запись
  возвращается как есть; приостановленная/завершённая → 422 `enrollment.not_active`.
- Ссылка-приглашение: `url = ${PUBLIC_BASE_URL}/join/{token}` (токен 256 бит, хранится хеш). `expiresAt` — в будущем,
  `maxUses` — 1..10000 (оба необязательны). `DELETE /invite-links/{id}` — отзыв. `accept`: неизвестный токен или ссылка
  другого tenant → 404 `enrollment.invite_not_found`; истекла/отозвана/исчерпана → 422 `enrollment.invite_invalid`
  (`args.reason: expired|revoked|exhausted|valid`); уже участник → `{ courseId }` без расхода использования.
- Группы: имя уникально в курсе (409 `group.name_taken`); `PUT /groups/{id}/members` — только записанные на курс
  (400 `userIds: not_enrolled`); `groups/auto` распределяет **активных студентов** случайно: `by_count` — N групп
  (не больше числа студентов), `by_size` — группы по ≤ N человек; `value` 1..1000; имена «<prefix> N» (по умолчанию
  «Группа»/«Group»), занятые номера пропускаются.

### 6.1. Приглашение преподавателем (`identity`)

| POST | `/courses/{id}/invitations` | `{ email, firstName, lastName, role: CourseRole }` → 201 `{ userId, accountCreated, activationUrl }` |
|---|---|---|
| GET | `/courses/{id}/enrollment-candidates?q&cursor` | `Page<UserSummary>` — активные пользователи школы для «Записать пользователей» |

Право `enrollment.manage` на курсе (преподаватель курса или владелец школы). Пользователь ищется по email в школе;
нет — создаётся приглашённый (`origin = tutor_invite`, `createdBy` = пригласивший). Затем запись на курс с ролью
(`method = manual`, повторная — реактивирует). `activationUrl` — одноразовая ссылка установки пароля, пока аккаунт не
активирован (письмо с ней уходит через notifier, если настроен SMTP); для активного аккаунта — `null`.
Заблокированного в школе или платформой пригласить нельзя — 422 `user.blocked`. Кандидаты не включают
заблокированных платформой.

## 7. Файлы (`files`)

| POST | `/files/uploads` | `{ fileName, contentType, size, purpose: 'content'|'submission'|'avatar'|'cover'|'video'|'import' }` → `{ fileId, uploadUrl, headers: Record<string,string>, expiresAt }` |
|---|---|---|
| POST | `/files/{id}/complete` | → `FileMeta` (проверка размера и сигнатуры; видео → транскодирование) |
| GET | `/files/{id}` | `FileMeta` |
| GET | `/files/{id}/download` | 302 на pre-signed GET (права проверяются) |

```ts
type FileMeta = { id: Id; name: string; size: number; mime: string; status: 'pending'|'ready'|'rejected'; url: string | null;
                  video?: { status: 'processing'|'ready'|'failed'; hlsUrl: string | null; durationSec: number | null } }
```

Детали (files):
- `POST /files/uploads` → 201. Лимиты: `avatar`, `cover` — 5 МБ, изображения (png, jpeg, gif, webp); `content`, `submission` — 100 МБ, документы/изображения/аудио/видео; `import` — 100 МБ, csv/txt/xlsx; `video` — `S3_MAX_FILE_SIZE` (mp4, mov, webm, mkv). Клиент обязан выполнить PUT с заголовками из `headers` (подписан `Content-Type`). Квота школы → 422 `files.quota_exceeded`.
- `POST /files/{id}/complete` — только загрузивший. Ошибки: 422 `files.not_uploaded` (объекта нет, можно повторить), `files.size_mismatch`, `files.type_mismatch` (содержимое не совпало с типом — файл переводится в `rejected`), `files.rejected`. Повторный вызов для `ready` идемпотентен.
- `GET /files/{id}` и `/download`: загрузивший или пользователь с правом на владельца файла (элемент курса, сдача, пост, профиль…); иначе 404 `files.not_found`. `url` — pre-signed GET (TTL 10 мин), небезопасные типы отдаются с `Content-Disposition: attachment`. `/download` для неготового файла → 422 `files.not_ready`.
- `video.hlsUrl` — публичный URL мастер-плейлиста (ADR-008). Готовность видео приходит уведомлением категории `video_ready`.

## 8. Задания и проверка (`assessment`, `gradebook`)

| GET | `/items/{id}/my-submission` | `Submission` (текущая попытка студента, создаётся черновик при первом обращении) |
|---|---|---|
| PUT | `/items/{id}/my-submission/draft` | `{ text?: BlockDoc, fileIds?: Id[] }` → `Submission` (автосохранение, UX-03) |
| POST | `/items/{id}/my-submission/submit` | `Idempotency-Key` → `Submission` |
| GET | `/items/{id}/submissions?status&groupId&cursor` | `Page<SubmissionSummary>` (teacher) |
| GET | `/submissions/{id}` | `Submission` |
| POST | `/submissions/{id}/grade` | `{ score: number | null, feedback?: BlockDoc, feedbackFileIds?: Id[], returnForRevision?: boolean }` → `Submission` |
| POST | `/items/{id}/grades/publish` | → `{ published: number }` (FR-ASSIGN-07) |
| POST | `/items/{id}/extensions` | `{ userId?: Id, groupId?: Id, dueAt: Instant, closeAt?: Instant }` → `Extension` (FR-ASSIGN-03; повтор для того же студента/группы заменяет продление) |
| GET | `/items/{id}/extensions` | `Extension[]` |
| DELETE | `/extensions/{id}` | 204 |
| GET | `/grading-queue?courseId&type&cursor&limit` | `Page<QueueEntry>` (FR-GRADE-06, отсортировано по сроку) |

```ts
type SubmissionStatus = 'draft' | 'submitted' | 'submitted_late' | 'graded' | 'returned'
type Submission = { id: Id; itemId: Id; userId: Id; userName: string; attemptNo: number; status: SubmissionStatus;
                    text: BlockDoc | null; files: FileMeta[]; submittedAt: Instant | null; dueAt: Instant | null; late: boolean;
                    grade: { score: number | null; maxScore: number; published: boolean; feedback: BlockDoc | null; feedbackFiles: FileMeta[]; gradedAt: Instant | null; graderName: string | null } | null;
                    history: { attemptNo: number; status: SubmissionStatus; submittedAt: Instant | null; score: number | null }[];
                    version: number }
type SubmissionSummary = { id: Id; userId: Id; userName: string; status: SubmissionStatus; submittedAt: Instant | null; late: boolean; score: number | null }
type QueueEntry = { kind: 'submission' | 'essay'; id: Id; courseId: Id; courseTitle: string; itemId: Id; itemTitle: string;
                    userId: Id; userName: string; submittedAt: Instant; dueAt: Instant | null; late: boolean }
type Extension = { id: Id; itemId: Id; userId: Id | null; groupId: Id | null; dueAt: Instant; closeAt: Instant | null }
```
Для `kind: 'essay'` `id` = `attemptId:slot`, оценка — `POST /attempts/{attemptId}/answers/{slot}/grade { score, comment? }`.

Детали (задания):
- Права: студент — `submission.submit` (и элемент виден студентам, иначе 404 `assignment.not_found`); список/просмотр чужих — `submission.viewAll`; оценка и продления — `submission.grade`; публикация — `grade.publish`. Ассистент в курсе с `groupMode: 'separate'` видит и проверяет только работы участников своих групп (чужие → 404 `submission.not_found`).
- `PUT .../draft`: отсутствующее поле не меняет сохранённое значение (`text: {schemaVersion:1,blocks:[]}` / `fileIds: []` — очистить). Файлы — готовые (`status: ready`) и загруженные самим студентом (иначе 400 `fileIds`/`file_not_owned`); ограничения настроек → 400 с `errors[].code` из `files_not_allowed | text_not_allowed | too_many_files | file_too_large | extension_not_allowed`. Текст санитизируется (embed запрещены). До `openAt` → 422 `assignment.not_open`, после действующего `closeAt` → 422 `assignment.closed`; отправленную работу менять нельзя → 422 `assignment.not_editable` (кроме `requireSubmitButton: false`: тогда сохранение с содержимым сразу считается сдачей, правка возможна до проверки).
- `POST .../submit` (AC-3): время сдачи — время первой успешной обработки; повтор с тем же ключом возвращает ту же сдачу. Ошибки 422: `assignment.empty_submission`, `assignment.already_submitted`, `assignment.offline` (`submissionType: 'none'`), `assignment.not_open`, `assignment.closed`. `late` — сдача позже действующего срока (продление ⊕ настройки). После возврата на доработку (`returned`) следующий PUT/submit начинает новую попытку с копией содержимого; лимит `maxAttempts` → 422 `assignment.attempts_exhausted`.
- Групповая сдача (`groupSubmission: true`): одна попытка на группу (первая по id группа студента), оценка записывается каждому участнику.
- `status` в списке сдач: любой `SubmissionStatus`, а также `not_graded` (submitted + submitted_late) и `late`; пагинация по времени сдачи (черновики — по времени создания), по возрастанию.
- `POST /submissions/{id}/grade`: только текущая попытка (иначе 422 `assignment.not_latest_attempt`); `score` — от 0 до `maxScore`, не более 2 знаков (иначе 400 `score`/`out_of_range`); `returnForRevision` → статус `returned`, иначе при наличии `score` → `graded`. Оценка публикуется сразу при `autoPublishGrades` (или если уже была опубликована), иначе — через `POST /items/{id}/grades/publish`. Студент видит `grade` после публикации; при возврате на доработку — отзыв сразу, балл — после публикации.

## 9. Журнал оценок (`gradebook`)

| GET | `/courses/{id}/gradebook?groupId` | `Gradebook` |
|---|---|---|
| PUT | `/courses/{id}/gradebook/setup` | `{ aggregation: 'weighted_mean'|'sum', categories: { id?: Id; name: string; weight: number }[], items: { gradeItemId: Id; categoryId: Id | null }[], scaleId?: Id | null }` → `GradebookSetup` |
| GET | `/courses/{id}/gradebook/setup` | `GradebookSetup` |
| PATCH | `/grades/{id}` | `{ score: number | null, locked?: boolean, version }` (FR-GRADE-04) |
| POST | `/courses/{id}/gradebook/manual-items` | `{ name, maxScore, categoryId? }` |
| PUT | `/courses/{id}/gradebook/cells` | `{ gradeItemId, userId, score }` → `GradeCell` (создаёт оценку, если нет) |
| GET | `/grades/{id}/history` | `GradeHistoryEntry[]` |
| GET | `/courses/{id}/gradebook/export?format=csv|xlsx` | файл |
| GET | `/me/grades/{courseId}` | `MyCourseGrades` |
| GET/POST | `/scales` | `Scale[]` / `{ name, levels: { name: string; minPercent: number }[] }` |

```ts
type GradebookSetup = { aggregation: 'weighted_mean'|'sum'; categories: { id: Id; name: string; weight: number }[];
                        items: { gradeItemId: Id; name: string; maxScore: number; categoryId: Id | null; sourceItemId: Id | null }[];
                        scaleId: Id | null;
                        formula: string;                  // "Итог = 0,4 × Задания + 0,5 × Тесты" (FR-GRADE-03)
                        warnings: { code: 'weights_not_100' | 'empty_category' | 'zero_max' ; message: string }[] }
type GradeCell = { gradeId: Id | null; score: number | null; overridden: boolean; locked: boolean; published: boolean; version: number }
type Gradebook = { columns: { gradeItemId: Id; name: string; maxScore: number; categoryId: Id | null }[];
                   rows: { userId: Id; userName: string; cells: Record<Id, GradeCell>; finalPercent: number | null; finalLabel: string | null }[] }
type GradeHistoryEntry = { at: Instant; actorName: string; oldScore: number | null; newScore: number | null }
type MyCourseGrades = { courseId: Id; items: { gradeItemId: Id; name: string; score: number | null; maxScore: number; feedback: BlockDoc | null }[]; finalPercent: number | null; finalLabel: string | null }
type Scale = { id: Id; name: string; levels: { name: string; minPercent: number }[]; courseId: Id | null }
```

Детали (журнал):
- Права: просмотр журнала, настройки и истории — `grade.viewAll`; изменение настройки и ручные столбцы — `gradebook.configure`; ячейки и `PATCH /grades/{id}` — `grade.edit`; экспорт — `grade.export`; «Мои оценки» — `grade.viewOwn`.
- `weight` категории — проценты 0..100. Итог (`weighted_mean`): внутри категории — сумма баллов / сумма максимумов оценённых элементов, затем среднее по категориям с весами, нормированное на сумму весов категорий, где есть оценки; элементы без категории в этом режиме не учитываются; без категорий и в режиме `sum` — сумма баллов / сумма максимумов. Элементы без оценки и с максимумом 0 не учитываются. Проценты округляются до 2 знаков. `finalLabel` — по шкале курса (`scaleId`). В журнале преподавателя итог считается по всем оценкам, в «Моих оценках» и `GradebookApi.finalPercent` — только по опубликованным.
- `PUT .../setup`: категории без `id` создаются, отсутствующие в списке — удаляются (их элементы остаются без категории); `items` — только перепривязка категорий. `formula`/`warnings` локализуются по `Accept-Language`.
- `POST .../manual-items` → 201 `GradebookSetup.items[]`-элемент. Значения ручных столбцов публикуются сразу.
- `PUT .../cells`: `userId` — студент курса (иначе 400 `userId`/`not_student`); для столбца элемента курса значение становится переопределением (`overridden: true`) и больше не перезаписывается источником. `PATCH /grades/{id}` → `GradeCell`; конфликт версии → 412 `conflict.version`. Каждое изменение балла пишется в историю (append-only) и в аудит.
- Экспорт: CSV (UTF-8 с BOM, разделитель `;`, защита от формул) или XLSX; столбцы: студент, элементы («название / максимум»), итог %, оценка.
- `GET /me/grades` → `MyGradesOverview` (курсы, где пользователь — студент). `MyCourseGrades.items[].score` — только опубликованные оценки, `feedback` — опубликованный отзыв задания.
- `/scales?courseId`: шкалы tenant (+ курса). При первом обращении tenant получает шкалы «Пятибалльная» (отлично ≥ 85, хорошо ≥ 70, удовлетворительно ≥ 50, неудовлетворительно ≥ 0) и «Зачёт/незачёт» (зачёт ≥ 60). `POST /scales` `{ name, levels, courseId? }`: без `courseId` — шкала tenant (`tenant.manage`), с `courseId` — шкала курса (`gradebook.configure`); повтор имени шкалы tenant → 400 `name`/`duplicate`.
- `/grading-queue`: курсы, где пользователь — teacher/assistant с `submission.grade` (или указанный `courseId`); `type`: `submission | essay`; сортировка: срок (без срока — в конце), время сдачи; `cursor` — непрозрачный keyset-курсор.

## 10. Банк вопросов и тесты (`assessment`)

| GET/POST | `/courses/{id}/question-bank/categories` | `QCategory[]` / `{ name, parentId? }` |
|---|---|---|
| GET | `/courses/{id}/questions?categoryId&tag&type&q&cursor` | `Page<QuestionSummary>` |
| POST | `/courses/{id}/questions` | `QuestionInput` → `Question` |
| GET | `/questions/{id}` | `Question` (текущая версия, с ключами — только qbank.manage) |
| PUT | `/questions/{id}` | `QuestionInput` → `Question` (создаёт новую версию, FR-QBANK-05) |
| DELETE | `/questions/{id}` | 204 |
| GET | `/questions/{id}/versions` | `{ version: number; createdAt: Instant; id: Id }[]` |
| POST | `/questions/{id}/preview-check` | `{ response: QuestionResponse }` → `{ score: number; maxScore: number; correct: boolean }` |
| PUT | `/items/{id}/quiz/slots` | `{ slots: ({ questionId: Id; points?: number; page: number } | { random: { categoryId?: Id; tag?: string; count: number }; points?: number; page: number })[] }` |
| GET | `/items/{id}/quiz/slots` | `{ slots: QuizSlot[]; questions: QuestionSummary[] }` (слоты + фиксированные вопросы) |
| POST | `/items/{id}/attempts` | → `Attempt` (начать/продолжить) |
| GET | `/attempts/{id}` | `Attempt` (для студента — без ключей, NFR-SEC-08) |
| PUT | `/attempts/{id}/answers/{slot}` | `{ response: QuestionResponse \| null, flagged?: boolean }` → `{ savedAt: Instant }` (409 `quiz.time_expired` после `timeDue` + допуск; `response: null` — только пометка) |
| POST | `/attempts/{id}/finish` | `Idempotency-Key` → `AttemptResult` |
| GET | `/attempts/{id}/result` | `AttemptResult` (по правилам показа FR-QUIZ-05) |
| GET | `/items/{id}/attempts?cursor` | `Page<AttemptSummary>` (teacher, FR-QUIZ-07) |
| POST | `/items/{id}/regrade` | → `{ regraded: number }` (AC-5) |
| POST | `/items/{id}/overrides` | `{ userId?: Id; groupId?: Id; openAt?; closeAt?; timeLimitSec?; maxAttempts? }` → 201 `QuizOverride` (FR-QUIZ-06; заменяет исключение того же пользователя/группы) |
| GET | `/items/{id}/overrides` | `QuizOverride[]` |
| DELETE | `/items/{id}/overrides/{overrideId}` | 204 |
| POST | `/attempts/{id}/answers/{slot}/grade` | `{ score, comment? }` → `AttemptResult` (эссе, право `submission.grade`) |

```ts
type QuestionType = 'single_choice' | 'multiple_choice' | 'true_false' | 'short_answer' | 'numerical' | 'essay' | 'matching' | 'ordering'
type QuestionInput = { type: QuestionType; title: string; body: BlockDoc; defaultScore: number; categoryId: Id | null; tags: string[];
                       data: QuestionData; generalFeedback?: BlockDoc | null }
type QuestionData =
  | { type: 'single_choice'; options: { id: string; text: string; correct: boolean; feedback?: string }[]; shuffle: boolean }
  | { type: 'multiple_choice'; options: { id: string; text: string; correct: boolean; feedback?: string }[]; shuffle: boolean;
      scoring: 'all_or_nothing' | 'partial' | 'partial_with_penalty' }
  | { type: 'true_false'; correct: boolean }
  | { type: 'short_answer'; answers: { pattern: string; scorePercent: number }[]; caseSensitive: boolean }   // '*' — любой набор символов
  | { type: 'numerical'; answers: { value: number; tolerance: number; scorePercent: number }[] }
  | { type: 'essay'; responseFormat: 'text' | 'text_and_files'; minWords?: number; maxWords?: number }
  | { type: 'matching'; pairs: { id: string; prompt: string; answer: string }[]; shuffle: boolean }
  | { type: 'ordering'; items: { id: string; text: string }[] }                     // правильный порядок = порядок массива
type QuestionResponse =
  | { optionId: string } | { optionIds: string[] } | { value: boolean } | { text: string } | { number: number }
  | { essay: BlockDoc; fileIds?: Id[] } | { matches: Record<string, string> } | { order: string[] }
type Question = { id: Id; version: number; versionId: Id } & QuestionInput
type QuestionSummary = { id: Id; type: QuestionType; title: string; tags: string[]; version: number; updatedAt: Instant; usedInQuizzes: number }
type QCategory = { id: Id; parentId: Id | null; name: string; questionCount: number }
type QuizSettings = { kind: 'quiz'; openAt: Instant | null; closeAt: Instant | null; timeLimitSec: number | null; maxAttempts: number | null;
  gradingMethod: 'highest' | 'last' | 'average' | 'first'; passPercent: number | null; shuffleQuestions: boolean; shuffleAnswers: boolean;
  questionsPerPage: number; maxScore: number;
  review: { whenScore: 'immediately'|'after_close'|'never'; whenCorrectness: 'immediately'|'after_close'|'never'; whenCorrectAnswers: 'immediately'|'after_close'|'never'; whenFeedback: 'immediately'|'after_close'|'never' };
  gradeCategoryId: Id | null }
type StudentQuestionView = { slot: number; page: number; points: number; type: QuestionType; title: string; body: BlockDoc;
  // только «публичные» части data: варианты без correct, пары с перемешанными ответами, элементы ordering перемешаны
  options?: { id: string; text: string }[]; prompts?: { id: string; text: string }[]; answerChoices?: string[]; items?: { id: string; text: string }[];
  responseFormat?: 'text' | 'text_and_files'
  response: QuestionResponse | null; flagged: boolean }
type Attempt = { id: Id; itemId: Id; number: number; state: 'in_progress' | 'finished' | 'abandoned'; startedAt: Instant;
                 timeDue: Instant | null; serverNow: Instant; questions: StudentQuestionView[]; totalPages: number }
type AttemptResult = { id: Id; state: 'finished'; score: number | null; maxScore: number; percent: number | null; passed: boolean | null;
                       needsManualGrading: boolean;
                       questions: { slot: number; title: string; score: number | null; points: number; correct: boolean | null;
                                    response: QuestionResponse | null; correctResponse?: QuestionResponse; feedback?: string;
                                    comment?: string }[] }   // comment — комментарий проверяющего к эссе
type AttemptSummary = { id: Id; userId: Id; userName: string; number: number; state: string; startedAt: Instant; finishedAt: Instant | null; score: number | null; maxScore: number }
type QuizSlot = { questionId?: Id; random?: { categoryId: Id | null; tag: string | null; count: number }; points: number | null; page: number | null }
type QuizOverride = { id: Id; userId: Id | null; groupId: Id | null; openAt: Instant | null; closeAt: Instant | null; timeLimitSec: number | null; maxAttempts: number | null }
```

Детали (тесты, `assessment.quiz`):
- Права: банк вопросов — `qbank.manage`; состав, исключения, переоценка — `quiz.manage`; прохождение — `quiz.attempt` + доступность элемента студенту (видимость и условия; скрытый → 404 `item.not_found`, закрытый условиями → 403 `quiz.unavailable`); отчёт и чужие попытки — `quiz.viewReports`; проверка эссе — `submission.grade`. Чужой tenant → 404.
- Умолчания `QuizSettings`: без окна и лимита, попытки не ограничены, `gradingMethod: 'highest'`, `questionsPerPage: 5`, `maxScore: 10`, `shuffleAnswers: true`, `review`: балл/правильность/отзыв — `immediately`, правильные ответы — `after_close` (без `closeAt` «после закрытия» не наступает).
- Слоты: `page` необязателен (по умолчанию — по `questionsPerPage`), `points` — по умолчанию `defaultScore` вопроса; случайные вопросы тянутся при старте попытки без повторов; удалённые вопросы пропускаются. Порядок вариантов фиксируется в попытке (перезагрузка его не меняет); варианты ответов matching и элементы ordering перемешиваются всегда (ordering — никогда не в правильном порядке).
- Старт: незавершённая попытка продолжается; просроченная завершается сервером и начинается новая. 422 `quiz.not_open` / `quiz.closed` / `quiz.no_attempts_left` / `quiz.no_questions`. `timeDue = min(startedAt + timeLimitSec, closeAt)` с учётом исключений (исключение пользователя важнее групповых; из групповых берётся самое мягкое).
- Сохранение ответа после `timeDue` + `tutorcraft.quiz.time-grace` (5 с) → 409 `quiz.time_expired`; в завершённую попытку → 409 `quiz.attempt_finished`; чужая попытка → 404 `quiz.attempt_not_found`; нет слота → 404 `quiz.slot_not_found`. Просроченные попытки завершает фоновая задача (каждые 15 с), сохранённые ответы оцениваются.
- `GET /attempts/{id}` никогда не содержит ключей (`correct`, шаблоны, значения, отзывы вариантов) — ни для студента, ни для преподавателя. `GET /attempts/{id}/result` незавершённой попытки → 409 `quiz.attempt_in_progress`.
- Оценивание: multiple_choice `partial` = верные/всего_верных − неверные/всего_неверных, `partial_with_penalty` = (верные − неверные)/всего_верных (обе ≥ 0); short_answer/numerical — лучший процент подходящего варианта; matching — доля верных пар; ordering — доля элементов на своих местах; эссе — ручная проверка. Балл попытки масштабируется к `maxScore`.
- Журнал: итог по `gradingMethod` среди завершённых попыток записывается в gradebook; публикуется сразу при `review.whenScore = 'immediately'` и отсутствии непроверенных эссе, при `after_close` — автоматически в момент `closeAt`, при `never` — вручную (`POST /items/{id}/grades/publish`).
- Переоценка (`POST /items/{id}/regrade`): все завершённые попытки переводятся на текущие версии вопросов и перепроверяются (ручные оценки эссе сохраняются), журнал пересчитывается; история и уведомление `grade_published` об изменившейся опубликованной оценке — gradebook. До переоценки попытки остаются на своих версиях (DATA-02).
- Файлы в тексте вопроса привязываются к владельцу `question` (читают составители банка, проверяющие и студенты, у которых вопрос был в попытке); файлы эссе — к `attempt` (автор попытки и проверяющие), прикреплять можно только свои загрузки.

## 11. Форумы (`communication`)

| GET | `/items/{id}/discussions?cursor` | `Page<Discussion>` |
|---|---|---|
| POST | `/items/{id}/discussions` | `{ title, body: BlockDoc, mentions?: Id[] }` → 201 `Discussion` |
| GET | `/discussions/{id}` | `{ discussion: Discussion; posts: Post[] }` (дерево, глубина ≤ 3) |
| POST | `/discussions/{id}/posts` | `{ parentId: Id | null, body: BlockDoc, mentions?: Id[] }` → 201 `Post` |
| PATCH | `/posts/{id}` | `{ body }` (в пределах окна правки) |
| DELETE | `/posts/{id}` | 204 (автор в окне правки или модератор) |
| POST | `/discussions/{id}/pin` · `/lock` · `/subscribe` (DELETE — отменить) | 204 |
| POST | `/discussions/{id}/read` | 204 |
| POST | `/posts/{id}/hide` (DELETE — показать) | 204 (модератор) |

```ts
type ForumSettings = { kind: 'forum'; forumType: 'general' | 'qa' | 'announcements'; editWindowMinutes: number; gradeCategoryId: Id | null }
type Discussion = { id: Id; title: string; authorId: Id; authorName: string; pinned: boolean; locked: boolean; subscribed: boolean;
                    replyCount: number; unreadCount: number; lastPostAt: Instant; createdAt: Instant }
type Post = { id: Id; parentId: Id | null; authorId: Id; authorName: string; body: BlockDoc; createdAt: Instant; editedAt: Instant | null;
              canEdit: boolean; canDelete: boolean; hidden: boolean; children: Post[] }
```

Детали (форумы, `communication.forum`):
- Чтение — `content.view` и доступность элемента (как у тестов: скрыт → 404, закрыт условиями → 403 `forum.unavailable`); писать — `forum.post`; в форуме объявлений темы и ответы — только `forum.announce`; закрепление/блокировка/скрытие — `forum.moderate`.
- Умолчания `ForumSettings`: `forumType: 'general'`, `editWindowMinutes: 30` (0…10080).
- `parentId: null` — ответ на корневой пост темы. Глубина дерева ≤ 3 (корень — 0): ответ на пост глубины 3 прикрепляется к его родителю (плоское продолжение).
- Q&A: студент без своего поста в теме видит только корневой пост и свои посты. Скрытые посты (и их ветви) видят только модераторы.
- Правка/удаление своего поста — в пределах окна правки (422 `forum.edit_window_passed` / `forum.cannot_delete`; удалить можно только пост без ответов), модератор — всегда; удаление поста удаляет ветвь, удаление корневого поста — тему. Ответ в заблокированную тему → 422 `forum.discussion_locked` (кроме модераторов).
- Первая страница списка тем начинается со всех закреплённых тем, далее — по `lastPostAt`. `unreadCount` — чужие видимые посты после последней отметки `/read`.
- Автор темы/ответа автоматически подписывается на тему. Уведомления: объявление — всем активным студентам курса (`announcement`); ответ — подписчикам темы, кроме автора (`forum_reply`); `mentions` (≤ 20, только активные участники курса) — упомянутым (`forum_reply`). Ссылка в уведомлении: `/courses/{courseId}/items/{itemId}/discussions/{discussionId}`.
- Вложения постов привязываются к владельцу `post`. iframe-вставки в постах не разрешены.

## 12. Прогресс и отчёты (`progress`, `reporting`)

| GET | `/courses/{id}/completion/me` | `{ percent: number; completedAt: Instant | null; items: Record<Id, 'complete' | 'incomplete'> }` |
|---|---|---|
| GET | `/courses/{id}/reports/progress?groupId&format` | `{ items: { id: Id; title: string }[]; rows: { userId: Id; userName: string; completed: Id[]; percent: number; completedAt: Instant | null }[] }` или CSV |
| GET | `/audit-log?actorId&objectType&from&to&cursor` | `Page<AuditEntry>` |

Детали (прогресс, `progress`):
- `percent` — доля выполненных элементов с отслеживанием выполнения (`completionRule.mode ≠ 'none'`, не скрытых), с округлением вниз; без таких элементов — 0 (в `CourseCard.progressPercent` — null). Отчёт — `completion.viewAll`, строки — активные студенты (фильтр `groupId`), `format=csv` — `text/csv` UTF-8 с BOM: «Студент; Выполнено, %; Курс завершён; <по столбцу 1/0 на элемент>».
- Автовыполнение: `viewed` — открытие элемента, `submitted` — сдача задания или завершение попытки теста, `graded` — опубликованная оценка, `passed` — опубликованная оценка ≥ `passPercent` элемента (по умолчанию 50%), `posted` — пост в форуме; `auto` выполнено, когда наступили все события из `on`. Курс завершён, когда выполнены все `requiredItemIds` и/или итог ≥ `minFinalPercent`; дата фиксируется один раз.
- `POST/DELETE /items/{id}/complete` — только для `mode: 'manual'` (иначе 422 `progress.not_manual`); элемент должен быть открыт студенту (403 `progress.item_locked`).
- Условия доступа (`ConditionGroup`): оценка — по опубликованной оценке, `minPercent` включительно, `maxPercent` не включительно; дата — `[from, until)`. Недоступность модуля делает недоступными вложенные модули и элементы. `Availability.reasons` — первая строка «Откроется, когда: …» (условия через «; » для `all`, « или » для `any`), истёкший срок — отдельной строкой «Доступ закрыт …».

Журнал активности (`activity`, право `audit.view`, работает в школе из `X-Tenant-Id` главного администратора):

| Метод | Путь | Ответ |
|---|---|---|
| GET | `/activity-log?actor&kind&outcome&status&route&requestId&sessionId&from&to&includeAnonymous&cursor&limit` | `Page<ActivityEntry>` |
| GET | `/activity-log/summary?from&to&includeAnonymous` | `ActivitySummary` (по умолчанию последние 24 ч, окно ≤ 31 дня) |
| GET | `/activity-log/{id}/trail` | `ActivityTrail` |
| POST | `/activity/events` `{ events: ClientEvent[] }` (1–20) | 204 |

- `kind`: `request` (запрос к API) · `page_view` · `client_error`; `outcome`: `all` · `failed` (≥ 400 и ошибки браузера) · `errors` (≥ 500 и ошибки браузера).
- `actor` — подстрока e-mail/имени или точный IP; `route` — подстрока шаблона маршрута, пути или страницы.
- `includeAnonymous=true` добавляет записи без школы (вход, регистрация, публичные страницы) — только `platform.manage`, иначе 403 `activity.anonymous_forbidden`.
- Трассировка: запись ± окно (`trail-before` 30 мин / `trail-after` 5 мин) по тому же пользователю или вкладке; для анонимных — вкладка, иначе IP. Чужая школа → 404 `activity.not_found`.
- Не хранятся тела запросов, query-строки, пароли и токены; секретные переменные пути (`token`, `code`, `key`…) и e-mail/токены в текстах ошибок маскируются.
- `ClientEvent.kind` — только `page_view` или `client_error` (иначе 400); `occurredAt` старше часа или из будущего заменяется временем приёма.

```ts
type ActivityEntry = { id: Id; at: Instant; kind: 'request' | 'page_view' | 'client_error'; tenantId: Id | null; userId: Id | null;
  actorName: string | null; actorEmail: string | null; ip: string | null; userAgent: string | null; requestId: string | null;
  sessionId: string | null; page: string | null; method: string | null; route: string | null; path: string | null;
  pathParams: Record<string, string> | null; handler: string | null; status: number | null; durationMs: number | null;
  errorCode: string | null; errorType: string | null; errorMessage: string | null; errorStack: string | null /* только в trail */ }
type ActivityTrail = { focus: ActivityEntry; anchor: 'user' | 'session' | 'ip' | 'none'; from: Instant; to: Instant; events: ActivityEntry[]; truncated: boolean }
type ActivitySummary = { from: Instant; to: Instant; requests: number; failedRequests: number; serverErrors: number; clientErrors: number;
  activeUsers: number; p95DurationMs: number | null; topErrorRoutes: { method: string | null; route: string | null; count: number }[] }
type ClientEvent = { kind: 'page_view' | 'client_error'; page?: string; name?: string; message?: string; stack?: string; requestId?: string; occurredAt?: Instant }
```

```ts
type AuditEntry = { id: Id; at: Instant; actorId: Id | null; actorName: string | null; action: string; objectType: string; objectId: string; ip: string | null; diff: object | null }
```

## 13. Биллинг (`billing`) — только подписка школы (ADR-012)

Продажи курсов нет: единственный платёж на платформе — подписка школы (§13.2). Эндпоинты заказов
(`/courses/{id}/orders`, `/orders/{id}`, `/billing/orders`, `/billing/fake/{orderId}/pay`), вебхуки платёжных
провайдеров (`/billing/webhooks/{provider}`) и `PUT /courses/{id}/price` удалены.

```ts
type PublicCourse = { id: Id; slug: string; title: string; description: BlockDoc | null; coverUrl: string | null;
                      teacher: { name: string; avatarUrl: string | null }; modules: { title: string; itemCount: number }[];
                      selfEnrolEnabled: boolean; tenantSlug: string; tenantName: string }
```

### 13.2. Подписка школы на платформу

| GET | `/billing/subscription` | `Subscription` (`billing.manage` или `course.create` в tenant; иначе 403) |
|---|---|---|
| POST | `/billing/subscription/purchases` | `Idempotency-Key`, `{ term: 'month' \| 'quarter' \| 'year' }` → `Subscription` (`billing.manage`) |

```ts
type Subscription = { status: 'trial' | 'active' | 'expired'; trialEndsAt: Instant; paidUntil: Instant | null; accessUntil: Instant;
                      canManage: boolean; terms: { term: 'month' | 'quarter' | 'year'; months: number; price: Money }[];
                      payments: { id: Id; term: string; amount: Money; periodStart: Instant; periodEnd: Instant; createdAt: Instant }[] }
```

Детали (подписка):
- Все сроки открывают одинаковый функционал (без ограничений на число курсов и учеников); цены: месяц — 30 USD, 3 месяца — 75 USD, год — 150 USD.
- Пробный период 14 дней начинается при первом обращении к подписке (обычно сразу после регистрации школы).
- Без активной подписки школа работает только на чтение: создание, копирование и публикация курса (скрытый → опубликован/по расписанию) → 422 `billing.subscription_inactive`. Ученики продолжают учиться, преподаватели — проверять работы.
- Покупка продлевает доступ от его текущего конца (срок во время пробного периода или действующей подписки не теряется). Повтор с тем же `Idempotency-Key` не продлевает второй раз. `payments` — последние 20 оплат, только при `canManage`.
- Пока подключён только `PAYMENT_PROVIDER=fake`: срок активируется сразу, без страницы оплаты. С другим провайдером → 422 `billing.subscription_checkout_unavailable`.

## 14. Интеграции (`integrations`)

| GET/POST | `/tokens` | `ApiTokenSummary[]` / `{ name, scopes: string[], expiresAt? }` → `{ id, token }` (один раз) · DELETE `/tokens/{id}` |
|---|---|---|
| GET/POST | `/webhooks` | `Webhook[]` / `{ url, events: string[] }` → `{ id, secret }` (один раз) · DELETE `/webhooks/{id}` |
| GET | `/webhooks/{id}/deliveries` | `Page<WebhookDelivery>` |

```ts
type ApiTokenSummary = { id: Id; userId: Id; name: string; scopes: ('read' | 'write')[]; expiresAt: Instant | null; lastUsedAt: Instant | null; createdAt: Instant }
type Webhook = { id: Id; url: string; events: string[]; createdAt: Instant }
type WebhookDelivery = { id: Id; event: string; status: 'pending' | 'succeeded' | 'failed'; attempts: number; responseCode: number | null;
                         error: string | null; createdAt: Instant; lastAttemptAt: Instant | null; nextAttemptAt: Instant }
```

Права: `integration.manage`. Токен: `tcpat_<random>`, передаётся как `Authorization: Bearer tcpat_...` и действует от имени создавшего пользователя (его права); `read` — только GET/HEAD/OPTIONS (иначе 403 `integrations.token_read_only`), `write` — любые методы; отозванный/истёкший токен или приостановленный владелец → 401 `auth.invalid_token`.

События вебхуков: `enrollment.created`, `submission.submitted`, `grade.published`, `course.completed`. Подпись: заголовок `X-TC-Signature: t=<unix>,v1=<hex(hmac_sha256(secret, t + "." + body))>`.
Тело: `{ id: Id /* id доставки, для идемпотентности получателя */; event: string; occurredAt: Instant; data: object }`; `data`:
`enrollment.created { courseId, userId, role, method }`, `submission.submitted { courseId, itemId, userId, submissionId, late }`,
`grade.published { courseId, itemId, userId, score, maxScore }`, `course.completed { courseId, userId }`.
URL — только `https` на публичный адрес (частные/loopback/link-local адреса запрещены, проверка и при отправке); `http://localhost` — только в dev. Ответ 2xx — успех; иначе повтор через 30 с × 2^(n−1), максимум 8 попыток; таймаут 10 с; редиректы не выполняются.

## 15. Служебное

`GET /health/live`, `GET /health/ready` (NFR-OBS-03), `GET /api/v1/openapi.json`.
