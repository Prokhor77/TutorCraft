# Роли и разрешения (FR-ACL-01/02)

Источник истины в коде: `services/core-api/src/main/java/com/tutorcraft/core/access/domain/Permission.java` и `SystemRole.java`.

## Контексты
`PLATFORM → TENANT → CATEGORY (дерево) → COURSE → (MODULE → ITEM наследуют курс)`.
Итоговые права пользователя в контексте курса = объединение:
1. ролей уровня tenant (`tenant_admin` — всё в tenant);
2. `category_manager`, назначенного на категорию курса или любого её предка;
3. роли активной записи на курс (`enrollments.status = active` и текущее время в `[starts_at, ends_at)`).

## Разрешения (41)

| Разрешение | Смысл |
|---|---|
| `platform.manage` | управление tenant'ами, квотами |
| `tenant.manage` | настройки tenant, политика паролей |
| `tenant.branding` | логотип, цвета |
| `user.view` | просмотр пользователей tenant |
| `user.manage` | создание, правка, приостановка |
| `user.import` | импорт CSV |
| `user.impersonate` | вход как пользователь (P1) |
| `role.manage` | назначение ролей уровня tenant/категории |
| `category.manage` | CRUD категорий |
| `course.create` | создание курса |
| `course.view` | открыть курс |
| `course.viewHidden` | видеть скрытые/запланированные элементы |
| `course.edit` | структура, настройки, контент |
| `course.delete` | удаление в корзину и восстановление |
| `course.publish` | публикация, цена, лендинг |
| `content.view` | просмотр материалов |
| `enrollment.view` | список участников |
| `enrollment.manage` | запись, приостановка, ссылки-приглашения |
| `group.manage` | группы |
| `submission.submit` | сдавать работы |
| `submission.viewAll` | видеть чужие сдачи |
| `submission.grade` | проверять, возвращать на доработку |
| `grade.viewOwn` | свои оценки |
| `grade.viewAll` | журнал оценок курса |
| `grade.edit` | ручная правка оценок |
| `grade.publish` | публикация оценок |
| `grade.export` | экспорт журнала |
| `gradebook.configure` | категории, веса, шкалы |
| `quiz.attempt` | проходить тесты |
| `quiz.manage` | создавать тесты, исключения, переоценка |
| `quiz.viewReports` | отчёты по попыткам |
| `qbank.manage` | банк вопросов |
| `forum.post` | писать в форумы |
| `forum.moderate` | скрывать/удалять/закреплять/блокировать |
| `forum.announce` | писать в форум объявлений |
| `completion.viewAll` | прогресс всех участников |
| `report.view` | отчёты курса/tenant |
| `audit.view` | журнал аудита |
| `integration.manage` | API-токены, вебхуки |
| `billing.manage` | заказы, возвраты, платёжные настройки |
| `file.upload` | загрузка файлов |

## Системные роли

| Роль | Разрешения |
|---|---|
| `platform_admin` | все |
| `tenant_admin` | все, кроме `platform.manage` |
| `category_manager` | `category.manage`, `course.create`, `course.view`, `course.viewHidden`, `course.edit`, `course.delete`, `course.publish`, `content.view`, `enrollment.view`, `enrollment.manage`, `group.manage`, `user.view`, `report.view`, `file.upload` |
| `teacher` | `course.view`, `course.viewHidden`, `course.edit`, `course.delete`, `course.publish`, `content.view`, `enrollment.view`, `enrollment.manage`, `group.manage`, `submission.viewAll`, `submission.grade`, `grade.viewAll`, `grade.edit`, `grade.publish`, `grade.export`, `gradebook.configure`, `quiz.manage`, `quiz.viewReports`, `qbank.manage`, `forum.post`, `forum.moderate`, `forum.announce`, `completion.viewAll`, `report.view`, `file.upload` |
| `assistant` | `course.view`, `course.viewHidden`, `content.view`, `enrollment.view`, `submission.viewAll`, `submission.grade`, `grade.viewAll`, `grade.edit`, `quiz.viewReports`, `forum.post`, `forum.moderate`, `completion.viewAll`, `file.upload` |
| `student` | `course.view`, `content.view`, `submission.submit`, `grade.viewOwn`, `quiz.attempt`, `forum.post`, `file.upload` |
| `observer` | `course.view`, `content.view`, `grade.viewAll`, `completion.viewAll` |
| `guest` | `course.view`, `content.view` |

Системные роли неизменяемы; `tenant_admin` создаёт кастомные роли копированием (`POST /roles` с `copyFrom`).

Любой новый tenant при регистрации репетитора получает владельца с ролью `tenant_admin`.
