# Развёртывание TutorCraft на общем сервере с confeek

Сервер уже обслуживает confeek.com (репозиторий sproogeek). TutorCraft встаёт рядом отдельным
compose-проектом в своём каталоге и на своём домене и ничего из confeek не трогает.

## 1. Схема

```
посетитель → Cloudflare → reverse proxy на хосте (:443)
                            ├─ confeek.com          → 127.0.0.1:8080 → spruzhuk_app → backend …   (как было)
                            ├─ sproogeek.com        → 301 на confeek.com                            (как было)
                            └─ <домен TutorCraft>   → 127.0.0.1:3100 → tutorcraft_web (Next.js)
                                                         └─ /api/v1/* → core-api (внутри tutorcraft_net)
                                                    → 127.0.0.1:8190 → tutorcraft_notifier (/ws)
браузер ⇄ внешний S3 (загрузка по pre-signed URL, HLS-видео) — мимо сервера
```

| | confeek (sproogeek) | TutorCraft |
|---|---|---|
| Каталог | `$PROD_PATH` confeek | `/opt/tutorcraft` |
| Linux-пользователь деплоя | текущий `PROD_USER` | **новый** `tutorcraft` (см. §4.3) |
| Compose-проект / сеть | по имени каталога / `spruzhuk_net` | `tutorcraft` / `tutorcraft_net` |
| Контейнеры | `spruzhuk_*` | `tutorcraft_*` |
| Порты на хосте | `127.0.0.1:8080`, `127.0.0.1:5433` | `127.0.0.1:3100`, `127.0.0.1:8190`, `127.0.0.1:5434` |
| Образы | `ghcr.io/admst-dz/spruzhyk-*` | `ghcr.io/prokhor77/tutorcraft-*` |

Конфликтов по именам, портам, сетям и томам нет. Общими остаются только reverse proxy, RAM/CPU/диск.

## 2. Перед началом — проверить сервер

```bash
free -h                      # сколько свободно RAM
docker stats --no-stream     # сколько реально ест confeek
df -h /var/lib/docker        # место под образы и тома
nproc
ss -ltnp | grep -E ':(80|443)\b'                 # кто слушает 80/443: nginx, caddy или docker-proxy (traefik)
nginx -T 2>/dev/null | grep -E 'server_name|ssl_certificate|include' | sort -u   # если nginx
```

**Память — главный риск.** Лимиты confeek в `docker-compose.prod.yml` дают ≈ 7–8 ГБ потолка
(backend 4G, renderer 1.5G, material/glb по 512M + Postgres/Kafka/MinIO без лимитов), и в его
комментариях записано, что свободными оставалось ~4 ГБ до того, как backend получил ещё +1 ГБ.
TutorCraft в покое ≈ 2,5–3,5 ГБ (потолок лимитов ≈ 6 ГБ, пик — транскодирование видео).

- `available` в `free -h` ≥ 4 ГБ — можно запускать;
- 3–4 ГБ — запускать, но добавить swap 4 ГБ и следить за `docker stats` первые дни;
- < 3 ГБ — сначала увеличить RAM сервера. Иначе OOM-killer начнёт убивать контейнеры, в том числе confeek.

Кто на хосте терминирует HTTPS: в репозитории confeek встречаются и host-nginx (`edge/README-analytics-log.md`,
самое свежее описание), и Caddy (комментарий в compose), и Traefik (README). Команда `ss` выше даст ответ.
Ниже основной вариант — nginx; для Caddy — §4.6б; если это Traefik в Docker, схема с `127.0.0.1`-портами
не подходит (нужны labels и общая сеть) — конфиг придётся переделать.

## 3. Что уже подготовлено в репозитории

| Файл | Назначение |
|---|---|
| `docker-compose.prod.yml` | prod-стек: образы из GHCR, лимиты памяти/CPU, ротация логов, наружу только `127.0.0.1` |
| `.env.prod.example` | шаблон `.env` для сервера |
| `infra/deploy/nginx/tutorcraft.conf` | vhost для host-nginx: `/` → web, `/ws` → notifier, 80 → 443 |
| `infra/deploy/nginx/cloudflare-realip.conf` | реальный IP посетителя из `CF-Connecting-IP` (только для vhost TutorCraft) |
| `infra/deploy/backup.sh` | ежедневный дамп PostgreSQL + MongoDB с ротацией |
| `.github/workflows/_build-image.yml` | сборка образа в GHCR с тегами `latest` и `sha-<commit>` |
| `.github/workflows/deploy-production.yml` | после зелёного `ci` на `main`: сборка 4 образов → scp compose → `pull` + `up -d` → ожидание healthcheck'ов |

Отличия от dev-`docker-compose.yml`: нет SeaweedFS и Mailpit (внешние S3 и SMTP), нет публикации
Postgres/Mongo/Redis/Kafka наружу, у Kafka данные на томе и heap 512 МБ, у Mongo кэш 256 МБ,
у core-api `-Xmx1g` вместо `MaxRAMPercentage=75`, демо-сиды выключены, `WORKER_CONCURRENCY=1`.

## 4. Пошагово

### 4.0. Предусловие — зелёный CI
Реальная сборка Maven ещё не проходила. Деплой запускается только после успешного `ci` на `main`,
поэтому сначала `cd services/core-api && mvn verify` локально и исправить ошибки.

### 4.1. Домен и Cloudflare
1. Добавить домен в Cloudflare (отдельная зона), сменить NS у регистратора.
2. DNS: `A @ → IP сервера` и `CNAME www → @`, обе **Proxied** (оранжевое облако).
3. SSL/TLS → **Full (strict)**. Network → WebSockets: включено (по умолчанию).

### 4.2. Внешний S3 (пример — Yandex Object Storage)
1. Бакет `tutorcraft-prod`, доступ **ограниченный**; включить версионирование.
2. Сервисный аккаунт с ролью `storage.editor` → статический ключ → `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
3. CORS бакета: origin `https://<домен>`, методы `GET, PUT, HEAD`, заголовки `*`, expose `ETag`.
4. Публичное чтение только префикса `hls/` (ADR-008) — политикой бакета:
   `Effect: Allow, Principal: *, Action: s3:GetObject, Resource: tutorcraft-prod/hls/*`.
   Оригиналы и остальные файлы отдаются только по pre-signed URL.

### 4.3. Пользователь и каталог на сервере (от root)
```bash
adduser --disabled-password --gecos "" tutorcraft
usermod -aG docker tutorcraft
mkdir -p /opt/tutorcraft && chown tutorcraft:tutorcraft /opt/tutorcraft
# ключ для GitHub Actions: сгенерировать локально (ssh-keygen -t ed25519 -f tutorcraft_deploy -N ""),
# публичную часть — сюда, приватную — в секрет PROD_SSH_KEY
install -d -m 700 -o tutorcraft -g tutorcraft /home/tutorcraft/.ssh
cat tutorcraft_deploy.pub >> /home/tutorcraft/.ssh/authorized_keys
chown tutorcraft:tutorcraft /home/tutorcraft/.ssh/authorized_keys && chmod 600 /home/tutorcraft/.ssh/authorized_keys
```
**Почему отдельный пользователь.** `docker login ghcr.io` хранится в `~/.docker/config.json` — одна
учётка на реестр. Логин под `prokhor77` в пользователе confeek затрёт учётку `admst-dz`, и следующий
деплой confeek упадёт на `pull`. Изоляцией по безопасности это не является: группа `docker` ≈ root.

### 4.4. `.env` на сервере (под пользователем tutorcraft)
```bash
cd /opt/tutorcraft
# положить .env.prod.example (scp) и:
cp .env.prod.example .env && chmod 600 .env
for k in POSTGRES_PASSWORD REDIS_PASSWORD JWT_SECRET; do
  sed -i "s|^$k=.*|$k=$(openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-48)|" .env
done
sed -i "s|^DATA_ENCRYPTION_KEY=.*|DATA_ENCRYPTION_KEY=$(openssl rand -base64 32)|" .env
nano .env   # домен в WEB_ORIGIN/PUBLIC_BASE_URL, ключи S3, SMTP, OAuth, платежи
```
`DATA_ENCRYPTION_KEY` и `JWT_SECRET` после запуска не менять: первый шифрует секреты интеграций
в БД, смена второго разлогинит всех. Сохранить `.env` в менеджер паролей — без него бэкап БД бесполезен.

### 4.5. GitHub (репозиторий Prokhor77/TutorCraft → Settings)
Secrets → Actions:

| Секрет | Значение |
|---|---|
| `PROD_HOST` | IP сервера |
| `PROD_USER` | `tutorcraft` |
| `PROD_SSH_KEY` | приватный ключ `tutorcraft_deploy` |
| `PROD_PATH` | `/opt/tutorcraft` |
| `GHCR_PULL_USER` | `Prokhor77` |
| `GHCR_PULL_TOKEN` | classic PAT только с `read:packages` |

Variables → Actions: `PROD_DOMAIN` = домен без схемы (вшивается в `NEXT_PUBLIC_WS_URL=wss://<домен>/ws`).

### 4.6. TLS и reverse proxy

**Сертификат.** Проще всего — Cloudflare Origin CA (SSL/TLS → Origin Server → Create certificate, 15 лет):
сохранить в `/etc/nginx/ssl/<домен>.pem` и `.key` (`chmod 600` на ключ). Если для confeek используется
certbot — можно им же: `certbot certonly --webroot -w /var/www/certbot -d <домен> -d www.<домен>`
(vhost уже отдаёт `/.well-known/acme-challenge/` на 80-м порту) и поправить пути в конфиге.

**а) host-nginx** (от root):
```bash
cd /tmp && # скопировать сюда оба файла из infra/deploy/nginx/
sed -i 's/tutorcraft\.example/<домен>/g' tutorcraft.conf
cp cloudflare-realip.conf /etc/nginx/snippets/tutorcraft-cloudflare-realip.conf
cp tutorcraft.conf /etc/nginx/sites-available/tutorcraft.conf      # или /etc/nginx/conf.d/, если sites-* нет
ln -s /etc/nginx/sites-available/tutorcraft.conf /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx     # nginx -t обязателен: ошибка уронит и confeek
```
До первого деплоя домен отдаёт 502 — это нормально.

**б) если на хосте Caddy** — добавить в Caddyfile:
```
<домен> {
    tls /etc/caddy/ssl/<домен>.pem /etc/caddy/ssl/<домен>.key
    reverse_proxy /ws 127.0.0.1:8190
    reverse_proxy 127.0.0.1:3100 {
        header_up X-Forwarded-For {http.request.header.CF-Connecting-IP}
    }
}
www.<домен> {
    tls /etc/caddy/ssl/<домен>.pem /etc/caddy/ssl/<домен>.key
    redir https://<домен>{uri} permanent
}
```
затем `caddy validate --config /etc/caddy/Caddyfile && systemctl reload caddy`.

### 4.7. Первый деплой
GitHub → Actions → **Deploy to Production** → Run workflow → `all`. Workflow соберёт образы, зальёт
`docker-compose.prod.yml` + `infra/kafka/create-topics.sh` + `infra/deploy/backup.sh` в `/opt/tutorcraft`,
сделает `pull`, `up -d` и дождётся `healthy` у core-api, web, notifier, media-worker (до 5 минут на каждый).
Дальше каждый push в `main` → `ci` → автодеплой.

### 4.8. Проверка
```bash
cd /opt/tutorcraft
docker compose -f docker-compose.prod.yml ps          # все Up (healthy), kafka-init — Exited (0)
curl -sI https://<домен>/ | head -1                   # 200
docker stats --no-stream                              # память и TutorCraft, и confeek
ss -ltnp | grep -E '3100|8190|5434'                   # только 127.0.0.1
curl -sI https://confeek.com/ | head -1               # соседа не задели
```
В браузере: регистрация/вход, загрузка файла, короткое видео (статус «готово» приходит по WebSocket),
письмо со сбросом пароля. Первого администратора создать через регистрацию (демо-сиды выключены).

Внешние сервисы, которым нужен новый домен:
- Google OAuth: Authorized JavaScript origins → `https://<домен>`;
- Telegram: `/setdomain` у @BotFather → домен (для Login Widget);
- YooKassa/Stripe: вебхук `https://<домен>/api/v1/billing/webhooks/yookassa|stripe`.

### 4.9. Бэкапы
```bash
crontab -e    # под пользователем tutorcraft
0 3 * * * cd /opt/tutorcraft && ./infra/deploy/backup.sh >> backups/backup.log 2>&1
```
Дампы в `/opt/tutorcraft/backups`, хранятся 14 дней (`BACKUP_RETENTION_DAYS`). Копировать их с сервера
(rclone в тот же S3 в отдельный бакет) — бэкап на том же диске не спасает от потери сервера.

## 5. Откат
```bash
cd /opt/tutorcraft
echo 'TUTORCRAFT_IMAGE_TAG=sha-abc1234' >> .env        # тег из GHCR / истории Actions
docker compose -f docker-compose.prod.yml up -d
```
Миграции Flyway только вперёд: если в откатываемом релизе была миграция, сначала восстановить БД из бэкапа.
После исправления убрать строку `TUTORCRAFT_IMAGE_TAG` из `.env`.

## 6. Чего не делать на общем сервере
- `docker system prune -a`, `docker volume prune`, `docker compose down -v` — удалят образы/тома, в том числе confeek;
- публиковать порты без `127.0.0.1:` — Docker обходит UFW, сервис окажется в интернете в обход Cloudflare;
- собирать образы на сервере (`up --build`) — Maven и Next.js build съедят память продакшна confeek;
- запускать dev-`docker-compose.yml` на сервере — займёт порт 8080 у confeek;
- `systemctl reload nginx` без `nginx -t`.

## 7. Эксплуатация
```bash
cd /opt/tutorcraft
docker compose -f docker-compose.prod.yml logs -f --tail=200 core-api
docker compose -f docker-compose.prod.yml restart notifier
docker compose -f docker-compose.prod.yml exec postgres psql -U tutorcraft tutorcraft
```
DBeaver: SSH-туннель на сервер → `127.0.0.1:5434`.
