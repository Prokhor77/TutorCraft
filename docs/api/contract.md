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
                   traceId: string }
  ```
- Пагинация (API-04): `?cursor=&limit=` (по умолчанию 25, максимум 100). Ответ: `{ items: T[], nextCursor: string | null }` → тип `Page<T>`.
- Идемпотентность (API-05): заголовок `Idempotency-Key: <uuid>` обязателен для `POST /items/{id}/submissions/submit`, `POST /attempts/{id}/finish`, `POST /courses/{id}/orders`. Повтор с тем же ключом возвращает сохранённый ответ.
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

## 2. Профиль и «я» (`/me`)

| GET | `/me` | → `Me` |
|---|---|---|
| PATCH | `/me` | `{ firstName?, lastName?, timezone?, locale?, avatarFileId? }` → `Me` |
| POST | `/me/password` | `{ currentPassword, newPassword }` → 204 |
| POST | `/me/telegram/link` | → `{ deepLink: string }` (t.me/<bot>?start=<одноразовый код>) |
| GET | `/me/tasks` | → `MyTasks` (FR-DASH-01) |
| GET | `/me/teaching` | → `TeacherHome` (FR-DASH-02) |
| GET | `/me/grades` | → `MyGradesOverview` |
| GET | `/me/courses` | → `CourseCard[]` |
| GET | `/me/notifications?cursor` | → `Page<Notification>` + заголовок `X-Unread-Count` |
| POST | `/me/notifications/read` | `{ ids?: Id[]; all?: boolean }` → 204 |
| GET/PUT | `/me/notification-preferences` | `NotificationPreferences` |
| GET | `/me/calendar?from&to` | → `CalendarEvent[]` |
| POST | `/me/calendar/ical-token` | → `{ url }` (перевыпуск, старый отзывается) |
| GET | `/calendar/ical/{token}.ics` | публичный iCal |

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
type NotificationCategory = 'new_item' | 'deadline' | 'grade_published' | 'forum_reply' | 'announcement' | 'submission_received' | 'sale' | 'video_ready'
type NotificationChannel = 'web' | 'email' | 'telegram'
type NotificationPreferences = { matrix: Record<NotificationCategory, Record<NotificationChannel, boolean>> }
type CalendarEvent = { id: Id; title: string; startsAt: Instant; endsAt: Instant | null; courseId: Id | null; itemId: Id | null; kind: 'due' | 'open' | 'close' | 'personal' }
```

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
| PATCH | `/modules/{id}` | `{ title?, visibility?, publishAt?, conditions?, version }` |
| DELETE | `/modules/{id}` | 204 |
| POST | `/modules/{id}/move` | `{ position, parentId? }` |
| POST | `/modules/{id}/items` | `{ type: ItemType, title, settings?: object }` → `Item` |
| GET | `/items/{id}` | `ItemDetail` (для студента — без скрытых полей и ключей) |
| PATCH | `/items/{id}` | `{ title?, visibility?, publishAt?, settings?, content?: BlockDoc, completionRule?, conditions?, version }` |
| DELETE | `/items/{id}` | 204 |
| POST | `/items/{id}/restore` | 204 |
| POST | `/items/{id}/move` | `{ moduleId, position }` |
| POST | `/items/{id}/duplicate` | → `Item` |
| POST | `/items/{id}/complete` | 204 (ручная отметка студентом) · DELETE — снять |
| GET | `/trash?courseId` | → `TrashEntry[]` |
| GET | `/public/{tenantSlug}/courses` | публичный каталог (SSR) → `PublicCourse[]` |
| GET | `/public/{tenantSlug}/courses/{courseSlug}` | → `PublicCourse` (лендинг) |

```ts
type ItemType = 'page' | 'file' | 'url' | 'folder' | 'video' | 'assignment' | 'quiz' | 'forum'
type CourseCard = { id: Id; title: string; shortName: string | null; coverUrl: string | null; categoryId: Id | null;
                    role: CourseRole | null; progressPercent: number | null; visibility: Visibility; price: Money | null }
type Money = { amountMinor: number; currency: string }  // 500000 RUB = 5000,00 ₽
type Course = { id: Id; title: string; shortName: string | null; slug: string; categoryId: Id | null; description: BlockDoc | null;
                coverFileId: Id | null; coverUrl: string | null; startsAt: Instant | null; endsAt: Instant | null;
                visibility: Visibility; publishAt: Instant | null;
                selfEnrol: { enabled: boolean; code: string | null; maxStudents: number | null; until: Instant | null };
                price: Money | null; completionRule: CourseCompletionRule; groupMode: 'none' | 'visible' | 'separate';
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
```

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
                    role: CourseRole; status: 'active'|'suspended'|'completed'; method: 'manual'|'self'|'invite_link'|'payment'|'import';
                    startsAt: Instant | null; endsAt: Instant | null; groupIds: Id[]; lastAccessAt: Instant | null }
type Group = { id: Id; name: string; memberIds: Id[] }
```

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

## 8. Задания и проверка (`assessment`, `gradebook`)

| GET | `/items/{id}/my-submission` | `Submission` (текущая попытка студента, создаётся черновик при первом обращении) |
|---|---|---|
| PUT | `/items/{id}/my-submission/draft` | `{ text?: BlockDoc, fileIds?: Id[] }` → `Submission` (автосохранение, UX-03) |
| POST | `/items/{id}/my-submission/submit` | `Idempotency-Key` → `Submission` |
| GET | `/items/{id}/submissions?status&groupId&cursor` | `Page<SubmissionSummary>` (teacher) |
| GET | `/submissions/{id}` | `Submission` |
| POST | `/submissions/{id}/grade` | `{ score: number | null, feedback?: BlockDoc, feedbackFileIds?: Id[], returnForRevision?: boolean }` → `Submission` |
| POST | `/items/{id}/grades/publish` | → `{ published: number }` (FR-ASSIGN-07) |
| POST | `/items/{id}/extensions` | `{ userId?: Id, groupId?: Id, dueAt: Instant, closeAt?: Instant }` (FR-ASSIGN-03) |
| GET | `/grading-queue?courseId&type&cursor` | `Page<QueueEntry>` (FR-GRADE-06, отсортировано по сроку) |

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
```
Для `kind: 'essay'` `id` = `attemptId:slot`, оценка — `POST /attempts/{attemptId}/answers/{slot}/grade { score, comment? }`.

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
type Scale = { id: Id; name: string; levels: { name: string; minPercent: number }[] }
```

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
| GET | `/items/{id}/quiz/slots` | то же + разрешённые вопросы |
| POST | `/items/{id}/attempts` | → `Attempt` (начать/продолжить) |
| GET | `/attempts/{id}` | `Attempt` (для студента — без ключей, NFR-SEC-08) |
| PUT | `/attempts/{id}/answers/{slot}` | `{ response: QuestionResponse, flagged?: boolean }` → `{ savedAt: Instant }` (409 `quiz.time_expired` после `timeDue` + допуск) |
| POST | `/attempts/{id}/finish` | `Idempotency-Key` → `AttemptResult` |
| GET | `/attempts/{id}/result` | `AttemptResult` (по правилам показа FR-QUIZ-05) |
| GET | `/items/{id}/attempts?cursor` | `Page<AttemptSummary>` (teacher, FR-QUIZ-07) |
| POST | `/items/{id}/regrade` | → `{ regraded: number }` (AC-5) |
| POST | `/items/{id}/overrides` | `{ userId?: Id; groupId?: Id; openAt?; closeAt?; timeLimitSec?; maxAttempts? }` (FR-QUIZ-06) |
| POST | `/attempts/{id}/answers/{slot}/grade` | `{ score, comment? }` (эссе) |

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
                                    response: QuestionResponse | null; correctResponse?: QuestionResponse; feedback?: string }[] }
type AttemptSummary = { id: Id; userId: Id; userName: string; number: number; state: string; startedAt: Instant; finishedAt: Instant | null; score: number | null; maxScore: number }
```

## 11. Форумы (`communication`)

| GET | `/items/{id}/discussions?cursor` | `Page<Discussion>` |
|---|---|---|
| POST | `/items/{id}/discussions` | `{ title, body: BlockDoc }` → `Discussion` |
| GET | `/discussions/{id}` | `{ discussion: Discussion; posts: Post[] }` (дерево, глубина ≤ 3) |
| POST | `/discussions/{id}/posts` | `{ parentId: Id | null, body: BlockDoc }` → `Post` |
| PATCH | `/posts/{id}` | `{ body }` (в пределах окна правки) |
| DELETE | `/posts/{id}` | 204 (автор в окне правки или модератор) |
| POST | `/discussions/{id}/pin` · `/lock` · `/subscribe` (DELETE — отменить) | 204 |
| POST | `/discussions/{id}/read` | 204 |

```ts
type ForumSettings = { kind: 'forum'; forumType: 'general' | 'qa' | 'announcements'; editWindowMinutes: number; gradeCategoryId: Id | null }
type Discussion = { id: Id; title: string; authorId: Id; authorName: string; pinned: boolean; locked: boolean; subscribed: boolean;
                    replyCount: number; unreadCount: number; lastPostAt: Instant; createdAt: Instant }
type Post = { id: Id; parentId: Id | null; authorId: Id; authorName: string; body: BlockDoc; createdAt: Instant; editedAt: Instant | null;
              canEdit: boolean; canDelete: boolean; hidden: boolean; children: Post[] }
```

## 12. Прогресс и отчёты (`progress`, `reporting`)

| GET | `/courses/{id}/completion/me` | `{ percent: number; completedAt: Instant | null; items: Record<Id, 'complete' | 'incomplete'> }` |
|---|---|---|
| GET | `/courses/{id}/reports/progress?groupId&format` | `{ items: { id: Id; title: string }[]; rows: { userId: Id; userName: string; completed: Id[]; percent: number; completedAt: Instant | null }[] }` или CSV |
| GET | `/audit-log?actorId&objectType&from&to&cursor` | `Page<AuditEntry>` |

```ts
type AuditEntry = { id: Id; at: Instant; actorId: Id | null; actorName: string | null; action: string; objectType: string; objectId: string; ip: string | null; diff: object | null }
```

## 13. Биллинг (`billing`, гибрид)

| PUT | `/courses/{id}/price` | `{ price: Money | null }` |
|---|---|---|
| POST | `/courses/{id}/orders` | `Idempotency-Key`, `{ returnUrl }` → `{ orderId, status, confirmationUrl: string | null }` |
| GET | `/orders/{id}` | `Order` |
| GET | `/billing/orders?courseId&cursor` | `Page<Order>` (teacher/admin) |
| POST | `/billing/webhooks/{provider}` | вебхук провайдера (подпись проверяется) |
| POST | `/billing/fake/{orderId}/pay` | только профиль `dev`: имитация успешной оплаты |

```ts
type Order = { id: Id; courseId: Id; courseTitle: string; buyerId: Id; buyerName: string; amount: Money; status: 'pending' | 'paid' | 'failed' | 'refunded' | 'canceled'; provider: string; createdAt: Instant; paidAt: Instant | null }
type PublicCourse = { id: Id; slug: string; title: string; description: BlockDoc | null; coverUrl: string | null; price: Money | null;
                      teacher: { name: string; avatarUrl: string | null }; modules: { title: string; itemCount: number }[];
                      selfEnrolEnabled: boolean; tenantSlug: string; tenantName: string }
```

## 14. Интеграции (`integrations`)

| GET/POST | `/tokens` | `ApiTokenSummary[]` / `{ name, scopes: string[], expiresAt? }` → `{ id, token }` (один раз) · DELETE `/tokens/{id}` |
|---|---|---|
| GET/POST | `/webhooks` | `Webhook[]` / `{ url, events: string[] }` → `{ id, secret }` (один раз) · DELETE `/webhooks/{id}` |
| GET | `/webhooks/{id}/deliveries` | `Page<WebhookDelivery>` |

События вебхуков: `enrollment.created`, `submission.submitted`, `grade.published`, `course.completed`, `order.paid`. Подпись: заголовок `X-TC-Signature: t=<unix>,v1=<hex(hmac_sha256(secret, t + "." + body))>`.

## 15. Служебное

`GET /health/live`, `GET /health/ready` (NFR-OBS-03), `GET /api/v1/openapi.json`.
