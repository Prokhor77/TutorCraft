# Развёртывание TutorCraft на 91.149.179.186 (голый IP, HTTP)

Сценарий: приложение открывается по `http://91.149.179.186`, домена и TLS нет, внешний S3 и
почта не подключены. Сервер общий с confeek — всё ниже сделано так, чтобы соседа не задеть.

Вариант с доменом, Cloudflare и внешним S3 описан отдельно в [`deployment.md`](deployment.md).

---

## 0. Что изменилось в репозитории под этот сценарий

| Файл | Что делает |
|---|---|
| `docker-compose.ip.yml` | оверлей к `docker-compose.prod.yml`: поднимает локальный SeaweedFS вместо внешнего S3 |
| `infra/s3/start-prod.sh` | SeaweedFS с анонимным чтением **только** префикса `hls/` (в dev-скрипте открыт весь бакет) |
| `infra/deploy/nginx/tutorcraft-ip.conf` | vhost на IP: лимиты запросов, блокировка сканеров, отдельный сервер `:9000` под S3 |
| `infra/deploy/nginx/tutorcraft-proxy.conf` | общий сниппет proxy-заголовков |
| `infra/deploy/bootstrap-ip.sh` | одноразовая подготовка сервера (пользователь, `.env`, vhost) |
| `services/core-api/.../application-prod.yml` | выключены Swagger, OpenAPI и `/actuator`; таймауты Tomcat; доверие `X-Forwarded-*` только прокси |
| `.github/workflows/e2e.yml` | e2e вынесен из `ci` — иначе автодеплой не запускался бы (см. §5) |

---

## 1. Состояние сервера (замер 2026-09-28)

| Проверка | Результат |
|---|---|
| Кто на :80 и :443 | host-nginx 1.24.0 (Ubuntu) — схема с `127.0.0.1`-портами подходит |
| Порты 9000, 3100, 8190, 9100, 5434 | свободны (`spruzhuk_minio 9000/tcp` — внутренний порт контейнера, на хост не опубликован) |
| Занято соседями | `:8080` confeek (`spruzhuk_app`), `:8082` `crm-crm-1`, `:5433` `spruzhuk_db` |
| Память | 7.9 ГБ всего, 3.2 ГБ занято, **4.6 ГБ available, swap = 0** |
| Соседей на хосте | **два**: confeek (`spruzhuk_*`) и crm |

Вывод по памяти: базовые лимиты `docker-compose.prod.yml` дают потолок ≈6.7 ГБ — это больше,
чем есть. В `docker-compose.ip.yml` они урезаны до **≈5.4 ГБ** (postgres/mongo 512M, kafka и
media-worker 768M, SeaweedFS 512M). Лимит — потолок, а не резерв: в покое стек занимает ~2.5 ГБ.

Но при нулевом swap любой пик (транскодирование видео + прогрев JVM) упирается в OOM-killer
сразу, а он выбирает жертву по объёму памяти, а не по владельцу — упасть может и confeek.
Поэтому swap ниже обязателен.

Если состав хоста изменится, перепроверить:

```bash
ss -ltnp | grep -E ':(80|443|9000|3100|8190)\b'
free -h; df -h /var/lib/docker; nproc
docker ps --format '{{.Names}}\t{{.Ports}}'
ufw status
```

`default_server` у соседа помехой не является: точное совпадение `server_name 91.149.179.186`
выигрывает у любого `default_server`.

---

## 2. Ключ деплоя (на своей машине)

```bash
ssh-keygen -t ed25519 -f ~/.ssh/tutorcraft_deploy -N "" -C "tutorcraft-deploy"
```

Публичную часть (`tutorcraft_deploy.pub`) — на сервер, приватную (`tutorcraft_deploy`) — в секрет
GitHub `PROD_SSH_KEY` целиком, вместе со строками `-----BEGIN/END-----`.

---

## 3. Подготовка сервера (один раз, от root)

### 3.1. Swap — сделать ДО первого деплоя

На сервере 7.9 ГБ RAM, три приложения и ноль swap. Это единственное изменение, которое стоит
внести на уровне хоста, и оно защищает все три приложения сразу: вместо мгновенного убийства
процесса ядро вытеснит холодные страницы на диск.

```bash
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab      # переживёт перезагрузку
sysctl -w vm.swappiness=10 && echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
free -h                                               # Swap: 4.0Gi
```

`swappiness=10` — не гонять в swap то, что активно используется; своп нужен как подушка на пике,
а не как штатный режим. Если `/` мало места, положите файл на раздел посвободнее (`df -h`).

Откатывается одной командой: `swapoff /swapfile && rm /swapfile` (и убрать строку из `/etc/fstab`).

### 3.2. Пользователь, `.env`, vhost

Скопируйте три файла на сервер и запустите bootstrap:

```bash
scp infra/deploy/bootstrap-ip.sh infra/deploy/nginx/tutorcraft-ip.conf \
    infra/deploy/nginx/tutorcraft-proxy.conf ~/.ssh/tutorcraft_deploy.pub \
    root@91.149.179.186:/tmp/
```

```bash
cd /tmp && bash bootstrap-ip.sh tutorcraft_deploy.pub
```

Скрипт идемпотентен: заводит пользователя `tutorcraft`, создаёт `/opt/tutorcraft` и `.env` со
случайными секретами, ставит vhost и перезагружает nginx **только после успешного `nginx -t`**.
Существующий `.env` он не перезаписывает, контейнеры и конфиги confeek не трогает.

Пароль администратора он на экран не печатает (чтобы не осел в истории терминала):

```bash
sudo grep ADMIN_PASSWORD /opt/tutorcraft/.env
```

Сохраните весь `/opt/tutorcraft/.env` в менеджер паролей: `DATA_ENCRYPTION_KEY` расшифровывает
секреты интеграций в БД, без него резервная копия базы бесполезна. Менять его после первого
запуска нельзя, `JWT_SECRET` — разлогинит всех.

---

## 4. Настройки GitHub (Settings → Secrets and variables → Actions)

**Secrets:**

| Секрет | Значение |
|---|---|
| `PROD_HOST` | `91.149.179.186` |
| `PROD_USER` | `tutorcraft` |
| `PROD_SSH_KEY` | приватный ключ `tutorcraft_deploy` целиком |
| `PROD_PATH` | `/opt/tutorcraft` |
| `GHCR_PULL_USER` | `Prokhor77` |
| `GHCR_PULL_TOKEN` | classic PAT **только** с правом `read:packages` |

**Variables:**

| Переменная | Значение |
|---|---|
| `PROD_WS_URL` | `ws://91.149.179.186/ws` |
| `PROD_S3_ORIGIN` | `http://91.149.179.186:9000` |

Обе вшиваются в JS-бандл при сборке образа и попадают в CSP. Пустой `PROD_S3_ORIGIN` = браузер
заблокирует загрузку файлов; неверная схема в `PROD_WS_URL` = не будет живых уведомлений.

---

## 5. Почему автодеплой раньше не мог сработать

`deploy-production.yml` стартует по `workflow_run` успешного `ci`. Зелёным `ci` быть не мог:

- job `e2e` сверял живую спецификацию с `docs/api/openapi.json`, которого в репозитории нет;
- он же поднимал весь compose-стек и гонял Playwright — тяжёлый и нестабильный шаг гейтил выкладку.

Теперь `e2e` живёт в отдельном workflow (`.github/workflows/e2e.yml`, по расписанию и вручную),
а сверка OpenAPI при отсутствии базового файла не валит сборку, а публикует сгенерированную
спецификацию артефактом `openapi-spec` — скачать и закоммитить в `docs/api/openapi.json`.

Сборка core-api проверена локально: **463 юнит-теста + ArchUnit проходят** (Maven 3.9, JDK 21).
Запись в `docs/ROADMAP.md` о том, что Maven ни разу не запускался, больше не актуальна.

---

## 6. Первый деплой

GitHub → Actions → **Deploy to Production** → Run workflow → `all`.

Workflow соберёт четыре образа в GHCR, зальёт compose-файлы и скрипты в `/opt/tutorcraft`,
сделает `pull`, `up -d` и дождётся `healthy` у s3, core-api, web, notifier и media-worker.
Дальше каждый push в `main` → зелёный `ci` → автодеплой.

Проверка:

```bash
cd /opt/tutorcraft
docker compose -f docker-compose.prod.yml -f docker-compose.ip.yml ps
curl -sI http://91.149.179.186/ | head -1          # 200
curl -sI http://91.149.179.186/api/v1/openapi.json | head -1   # 404 — спецификация закрыта
curl -sI https://confeek.com/ | head -1            # соседа не задели
docker stats --no-stream
free -h                                            # available не должен уйти ниже ~1 ГБ
```

Первые пару дней стоит поглядывать на память — стек соседствует с двумя чужими приложениями:

```bash
free -h; docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}'
dmesg -T | grep -i 'killed process' | tail          # пусто = OOM-killer не срабатывал
```

Если `available` регулярно уходит ниже 1 ГБ — снижать `core-api` (лимит **и** `-Xmx` в
`entrypoint`, они должны меняться вместе) либо добавлять RAM.

В браузере: `http://91.149.179.186` → вход под `ADMIN_EMAIL` / `ADMIN_PASSWORD` → создание курса.

---

## 7. Что сделано для защиты

**На уровне nginx** (`tutorcraft-ip.conf`):

- лимит общих запросов 40 r/s (burst 80) и API 10 r/s на IP;
- вход, регистрация, сброс пароля, OAuth и приём приглашений — 12 запросов в минуту на IP.
  Это дополняет, а не заменяет троттлинг в core-api (5 попыток на аккаунт и 30 на IP за 15 минут);
- не более 60 одновременных соединений с адреса;
- таймауты на тело и заголовки (20 с) — против slowloris;
- `return 444` на массовое сканирование: `.env`, `.git`, `.sql`, `.bak`, `.pem`, `wp-admin`,
  `phpmyadmin`, `*.php` — соединение закрывается молча, без траты ответа;
- `/api/v1/openapi.json`, `/api/docs`, `/actuator` закрыты;
- `server_tokens off` — версия nginx не светится.

**На уровне приложения:**

- профиль `prod` выключает Swagger UI, генерацию OpenAPI и HTTP-доступ к `/actuator/**`
  (healthcheck'и работают через собственный `HealthController`, минуя web-слой Actuator);
- `X-Forwarded-For` принимается только от loopback и докер-сетей — иначе клиент подделал бы
  заголовок и обошёл троттлинг входа, подставляя на каждую попытку новый IP;
- CSP `connect-src` сужен до своего origin, сокета и S3: раньше он разрешал `http:`/`https:`,
  то есть внедрённый скрипт мог бы отправить сессию на любой хост;
- `console.*` вырезается из клиентского бандла (кроме `console.error`);
- source maps не публикуются.

**На уровне контейнеров:**

- `no-new-privileges` у всех, `cap_drop: ALL` у четырёх контейнеров приложения (все они уже
  работают под non-root);
- наружу открыты только `127.0.0.1:3100`, `127.0.0.1:8190`, `127.0.0.1:9100` и `127.0.0.1:5434` —
  публикация без `127.0.0.1:` обошла бы UFW через цепочку `DOCKER-USER`;
- анонимное чтение в S3 ограничено префиксом `hls/`, остальное только по pre-signed URL.

### Что защитой **не** закрыто

1. **Трафик идёт открытым текстом.** Это прямое следствие выбора «голый IP без TLS»: пароли,
   access-токен и refresh-cookie видны любому на пути (Wi-Fi, провайдер, хостер). `COOKIE_SECURE`
   вынужденно `false`, иначе браузер выбросит cookie и вход будет слетать каждые 15 минут.
   Пока по IP ходят только вы — терпимо; перед тем как пускать учеников, нужен домен с TLS
   (это меняется в трёх строках `.env` и одной переменной `PROD_WS_URL`).
2. **`script-src` содержит `'unsafe-inline'`** — inline-бутстрап Next.js. Nonce-based CSP числится
   в TODO `apps/web/README.md` и требует отдельной работы.
3. **Группа `docker` равносильна root.** Отдельный пользователь `tutorcraft` нужен из-за
   `~/.docker/config.json` (одна учётка GHCR на пользователя), изоляцией от confeek он не является.

### Опционально: UFW и fail2ban

Сервер общий с двумя чужими приложениями, поэтому автоматически я это не включаю — неверное
правило отрежет confeek или crm. Если UFW уже активен, порт 9000 нужно открыть явно, иначе
браузер не сможет загружать файлы:

```bash
ufw status numbered                 # сначала посмотреть, что уже есть
ufw allow 9000/tcp comment 'tutorcraft s3'
```

Порт 9000 слушает nginx (не докер), поэтому UFW им управляет нормально. Если бы SeaweedFS
публиковался докером напрямую, правило бы не подействовало: публикация портов проходит мимо
цепочки UFW через `DOCKER-USER` — ровно поэтому контейнер привязан к `127.0.0.1:9100`.

fail2ban по логам nginx (`/var/log/nginx/tutorcraft.access.log`) имеет смысл ставить после того,
как вы увидите реальный профиль атак — до этого лимитов nginx достаточно.

---

## 8. Обфускация: что реально сделано и почему не больше

Запрос был «обфусцировать код». Честно о том, что здесь применимо:

- **Java (core-api) и Go (воркеры) на клиент не уходят вообще.** Их байткод и бинарники лежат
  только в образах на сервере. Обфусцировать их — значит защищаться от того, у кого уже есть root
  на хосте; на этом этапе он и так может всё. ProGuard поверх Spring Boot вдобавок ломает
  рефлексию (Jackson, маппинг строк JDBC по именам, имена бинов) — это риск падения прода ради
  нулевой выгоды, поэтому не делаю.
- **Go-бинарники уже собираются с `-trimpath -ldflags="-s -w"`** — таблицы символов и пути сборки
  вырезаны, это было в Dockerfile до меня.
- **Клиентский JS — единственное, что действительно отдаётся пользователю.** Next.js в production
  и так минифицирует и манглит имена. Что добавлено: отключены source maps (с ними минификация
  обратима в один клик в devtools) и вырезан `console.*`.
- **Убрана информация о внутреннем устройстве**: OpenAPI-спецификация, Swagger UI, `/actuator`,
  версия nginx, стектрейсы в ответах.

Дальше по этой линии двигаться некуда без вреда: JS, исполняемый в браузере, принципиально
доступен для чтения. Реальную защиту дают пункты §7, а не запутывание кода.

---

## 9. Откат и эксплуатация

```bash
cd /opt/tutorcraft
COMPOSE="docker compose -f docker-compose.prod.yml -f docker-compose.ip.yml"

$COMPOSE logs -f --tail=200 core-api
$COMPOSE restart notifier
echo 'TUTORCRAFT_IMAGE_TAG=sha-abc1234' >> .env && $COMPOSE up -d   # откат на прошлый образ
```

Миграции Flyway только вперёд: если в откатываемом релизе была миграция — сначала восстановить БД
из бэкапа. После исправления убрать строку `TUTORCRAFT_IMAGE_TAG` из `.env`.

Бэкапы (от пользователя `tutorcraft`):

```bash
crontab -e
0 3 * * * cd /opt/tutorcraft && ./infra/deploy/backup.sh >> backups/backup.log 2>&1
```

**Чего не делать на общем сервере:** `docker system prune -a`, `docker volume prune`,
`docker compose down -v` (снесут образы и тома confeek), сборку образов на сервере
(`up --build` — Maven и Next.js съедят память соседнего прода), `systemctl reload nginx` без `nginx -t`.
