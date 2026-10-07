# core-api: соглашения для разработчиков (обязательны)

Читать вместе с `docs/SPEC.md` (разделы 0, 5–9), `docs/architecture.md`, `docs/api/contract.md`, `docs/permissions.md`.

## Структура модуля
```
com.tutorcraft.core.<module>/
  <Module>Api.java, *Events.java, публичные record/enum   ← единственное, что видят другие модули
  spi/            порты, которые модуль ПРЕДОСТАВЛЯЕТ для реализации другими (пример: courses.spi.ActivityType)
  domain/         чистые правила, value objects, без Spring/JDBC/HTTP (unit-тесты без моков)
  application/    сервисы use case (@Service), интерфейсы репозиториев, транзакции, авторизация
  infrastructure/ реализации репозиториев (JdbcClient / MongoTemplate), адаптеры, слушатели Kafka
  web/            @RestController + request/response record'ы (DTO). Никакой логики, кроме маппинга.
```
- Другие модули используются **только** через их публичные классы корня пакета (`CoursesApi`, `ItemRef`, события) и `spi`. Запрещено импортировать чужие `application/infrastructure/web/domain`. Исключение: `access.domain` (Permission, AccessContext, CourseRole, TenantRole) — общий словарь прав. Проверяется `ModuleBoundariesTest` (ArchUnit).
- Реализация своего `<Module>Api` — класс в `application` с видимостью package-private.
- Исключение из правила импорта: `com.tutorcraft.core.seed` (сид демо-данных, только профиль `dev`) вызывает use case-сервисы модулей (`CourseCommandService`, `AccountProvisioner`, …), чтобы демо-данные проходили те же проверки, что и REST; SQL и репозитории чужих модулей в нём запрещены.
- Циклы бинов недопустимы: реализации SPI для access (`CourseLocator`, `CourseMembershipResolver`) зависят только от репозиториев.

## Код
- Java 21: `record` для DTO/value objects, `switch` с pattern matching, `var` — только когда тип очевиден.
- Функции ≤ 30 строк, guard clauses, без вложенных if-лестниц. Нет магических чисел/строк — `private static final` или `AppProperties`.
- Ошибки: `NotFoundException` / `ForbiddenException` / `ValidationException` / `ConflictException` / `BusinessRuleException` из `shared.domain` с кодом вида `<module>.<reason>`; тексты кодов — в `src/main/resources/i18n/<module>_ru.properties` и `<module>_en.properties` (+ копия ru в `<module>.properties` как fallback). Пустые catch запрещены.
- Логи: SLF4J, без ПДн/токенов/ответов студентов; только ID.
- Авторизация — **в application-слое** каждого use case: `access.require(Permission.X, AccessContext.course(courseId))`. Объект чужого tenant → 404 (репозитории всегда фильтруют по `tenant_id`/`tenantId`).
- Текущий пользователь — `CurrentUserProvider.require()` (userId, tenantId).
- Время — внедрённый `java.time.Clock`, никогда `Instant.now()` напрямую.
- Аудит: `AuditLog.record` — в транзакции изменения; `AuditLog.recordIndependently` — отдельная транзакция (REQUIRES_NEW) для событий безопасности, сопровождающих отказ (AC-1: чужой tenant → 404 + запись). `@Transactional(noRollbackFor = …)` для сохранения аудита не использовать.
- ID — `Ids.newId()` (UUIDv7).
- Входные данные: Bean Validation на DTO (`@NotBlank`, `@Size`) + доменная валидация через `shared.domain.Validator`.

## PostgreSQL
- Доступ — `JdbcClient` с именованными параметрами; SQL в текстовых блоках. Каждый запрос бизнес-данных содержит `tenant_id = :tenantId`.
- Даты: `Timestamps.of(instant)` / `Timestamps.read(rs, "col")`; jsonb: `JsonCodec.toJsonb(obj)` и `rs.getString("col")` + `json.read(...)` (в SELECT писать `col::text AS col`).
- Nullable-параметры в условиях фильтра: `(CAST(:x AS uuid) IS NULL OR col = :x)`.
- Миграции Flyway `src/main/resources/db/migration/V<N>__<name>.sql`, **номер закреплён за модулем** (см. ниже). Изменения схемы — expand/contract (NFR-REL-03).
- Мягкое удаление: `deleted_at`; корзина 30 дней (`tutorcraft.trash.retention`).
- Оптимистичная блокировка: колонка `version BIGINT`, `UPDATE ... WHERE version = :expected` → при 0 строк `ConflictException(IfMatch.VERSION_CONFLICT_CODE)`; контроллер берёт версию через `IfMatch.resolve(header, body.version())`.
- Пагинация: `PageQuery.of(cursor, limit)`, keyset `(sort_col, id) < (:afterAt, :afterId)`, `LIMIT :fetchSize`, затем `page.toPage(rows, sortKeyFn, idFn)`.

| Версия | Владелец | Таблицы |
|---|---|---|
| V1 | shared | outbox, processed_events, idempotency_keys, audit_log |
| V2 | org/identity | tenants, users, categories |
| V3 | access | roles, role_permissions, role_assignments |
| V4 | identity | refresh_tokens, password_reset_tokens, invitations, telegram_link_codes, user_import_previews |
| V5 | files | files, file_links, videos |
| V6 | courses | courses (+ индексы каталога) |
| V7 | enrollment | enrollments, course_invite_links, course_groups, course_group_members |
| V8 | assessment.assignment | submissions, submission_members, submission_files, submission_feedback, item_overrides |
| V9 | gradebook | grade_categories, grade_items, grades, grade_history, scales, gradebook_settings |
| V10 | communication.notifications | notifications, notification_deliveries, notification_preferences, calendar_personal_events, ical_tokens |
| V11 | billing | orders, payment_events (не используются с ADR-012; удаляются contract-миграцией) |
| V12 | assessment.quiz | quiz_attempts, attempt_answers, quiz_overrides, quiz_grade_releases |
| V13 | progress | completion_states, course_completions, item_views |
| V14 | communication.forum | forum_discussions, forum_posts, forum_subscriptions, forum_reads |
| V15 | integrations | api_tokens, webhooks, webhook_deliveries |
| V16 | activity | activity_log |
| V17 | billing | tenant_subscriptions, subscription_payments |
| V18 | identity | users: created_by, created_via, platform_blocked_at, platform_block_reason |
| V19 | communication.calendar | calendar_lessons, calendar_lesson_attendees; calendar_personal_events: description, all_day |
| V20 | audit | audit_log: индекс `(at, id)` для журнала всех школ |
| V21 | identity | users: `locale` допускает `uz` |

## MongoDB
- `MongoTemplate`, коллекции: `modules`, `items` (courses); `question_categories`, `questions`, `question_versions`, `quiz_layouts` (assessment.quiz).
- Поле `tenantId` в каждом документе; все запросы с `Criteria.where("tenantId").is(tenantId)`. UUID хранятся в стандартном представлении (`uuid-representation: standard`).
- Индексы создаются при старте в `infrastructure/<Module>MongoIndexes` (`@EventListener(ApplicationReadyEvent.class)`).
- Транзакций Mongo нет: операции идемпотентны (ADR-004).

## События
- Внутри монолита: `ApplicationEventPublisher.publishEvent(record)`. Слушатели, изменяющие данные своего модуля, — `@EventListener` (выполняются синхронно в транзакции публикатора — консистентность). Побочные эффекты наружу — только через `NotificationsApi`/`OutboxPublisher` (в той же транзакции).
- Kafka-консьюмеры: `@KafkaListener(topics = Topics.X)`, JSON-строка → `ObjectMapper`, в транзакции сначала `ProcessedEvents.markProcessed(eventId, "<consumer>")`.

## Идемпотентность
```java
return idempotency.execute(new IdempotencyScope(tenantId, userId, "submission.submit"), key, requestForHash, SubmissionView.class,
        () -> doSubmit(...));
```
Вызывать внутри `@Transactional` метода.

## Тесты
- Unit: `src/test/java/<тот же пакет>/*Test.java`, JUnit 5 + AssertJ + Mockito. Доменные правила (оценивание, условия доступа, права) — полное покрытие ветвей.
- Integration: `*IT.java`, `@Tag("integration")`, наследовать `com.tutorcraft.core.support.IntegrationTest` (Testcontainers Postgres/Mongo/Redis/Kafka, MockMvc, помощники авторизации). Обязательно проверять: разрешено / запрещено / чужой tenant (DoD).

## Правило против циклов бинов (важно)
- Реализации публичных `*Api` интерфейсов и SPI-адаптеров зависят **только** от репозиториев своего модуля и shared-ядра
  (`Clock`, `JsonCodec`, `ApplicationEventPublisher`, `AuditLog`, `OutboxPublisher`, `IdempotencyService`, `Messages`).
  Их держат в отдельных классах (`<Module>ApiImpl`, `<X>Adapter`), не в use-case сервисах.
- Use-case сервисы (`@Service`, вызываемые контроллерами) могут зависеть от чужих `*Api`, `AccessService`, `NotificationsApi`.
- Исключение: `NotificationsApi`-реализация может зависеть от `UsersApi` (он тоже «только репозитории»).

## Где что реализовано (владельцы публичных API)
| Интерфейс | Модуль-реализатор |
|---|---|
| `AccessService`, `AuditLog`, `OrgApi` (в т.ч. `embedWhitelist`, `storageQuotaMb`), `CategoryAncestry` | access / audit / org (готово) |
| `UsersApi`, `FilesApi`, `FileOwnerAccess('user')` | identity / files |
| `CoursesApi`, `CourseLocator`, `ActivityType(page,file,url,folder,video)`, `FileOwnerAccess('item','course')` | courses |
| `EnrollmentApi`, `CourseMembershipResolver` | enrollment |
| `GradebookApi` | gradebook |
| `ActivityType(assignment)`, `ItemStatusProvider(assignment)`, `GradingQueueSource(submission)`, `FileOwnerAccess('submission','feedback')` | assessment.assignment |
| `ActivityType(quiz)`, `ItemStatusProvider(quiz)`, `GradingQueueSource(essay)`, `FileOwnerAccess('attempt','question')` | assessment.quiz |
| `NotificationsApi` (в т.ч. `pushCounter` — счётчики WebSocket), порт `DueItemsSource` → `CoursesApi.itemsDueBetween` | communication.notifications |
| `courses.spi.CourseDataOwner` (удержание курса при очистке корзины, ADR-010) | enrollment, assessment.assignment, assessment.quiz, gradebook, billing, progress, communication.calendar (только очистка) |
| `ActivityType(forum)`, `FileOwnerAccess('post')`, `dashboard.spi.RecentPostsSource` | communication.forum |
| `LearnerStateProvider`, `ProgressApi`, `LearnerAccess`, `ConditionSchema` (статическая валидация условий) | progress |
