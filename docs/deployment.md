# Развёртывание TutorCraft на общем сервере с confeek (домен, Cloudflare, внешний S3)

Сервер уже обслуживает confeek.com (репозиторий sproogeek). TutorCraft встаёт рядом в k3s
(ADR-013): свой namespace `tutorcraft`, свой домен. Из confeek он ничего не трогает: соседи
остаются в Docker.

Базовая установка — k3s, пользователь деплоя, `.env`, CI — общая со сценарием «голый IP» и
описана в [`deployment-ip.md`](deployment-ip.md). Этот документ — про отличия: домен, TLS,
Cloudflare, внешние S3 и SMTP.

## 1. Схема

```
посетитель → Cloudflare → host-nginx (:443)
                            ├─ confeek.com          → 127.0.0.1:8080 → spruzhuk_app → backend …   (как было)
                            ├─ sproogeek.com        → 301 на confeek.com                            (как было)
                            └─ <домен TutorCraft>   → 10.43.0.100:3000 → Service web (Next.js)
                                                         └─ /api/v1/* → core-api (внутри кластера)
                                                    → 10.43.0.101:8090 → Service notifier (/ws)
браузер ⇄ внешний S3 (загрузка по pre-signed URL, HLS-видео) — мимо сервера
```

| | confeek (sproogeek) | TutorCraft |
|---|---|---|
| Каталог | `$PROD_PATH` confeek | `/opt/tutorcraft` (данные — `/opt/tutorcraft/data`) |
| Linux-пользователь деплоя | текущий `PROD_USER` | `tutorcraft`, без группы `docker`, kubeconfig на свой namespace |
| Оркестрация | Docker Compose, сеть `spruzhuk_net` | k3s, namespace `tutorcraft`, сети `10.42/16` и `10.43/16` |
| Вход с хоста | `127.0.0.1:8080`, `127.0.0.1:5433` | ClusterIP `10.43.0.100–102` (только с хоста) |
| Образы | `ghcr.io/admst-dz/spruzhyk-*` | `ghcr.io/prokhor77/tutorcraft-*` (containerd k3s, не Docker) |

Конфликтов по именам, портам, сетям и томам нет. Общими остаются только host-nginx, RAM, CPU и диск.

## 2. Перед началом — проверить сервер

```bash
free -h                      # сколько свободно RAM
docker stats --no-stream     # сколько реально ест confeek
df -h /var/lib/rancher /opt  # место под образы k3s и данные
nproc
ss -ltnp | grep -E ':(80|443)\b'                 # кто слушает 80/443: nginx, caddy или docker-proxy (traefik)
nginx -T 2>/dev/null | grep -E 'server_name|ssl_certificate|include' | sort -u   # если nginx
ip -4 route | grep -E '10\.4[23]\.'              # пусто: сети k3s свободны
```

**Память — главный риск.** Лимиты confeek в его `docker-compose.prod.yml` дают ≈ 7–8 ГБ потолка
(backend 4G, renderer 1.5G, material/glb по 512M + Postgres/Kafka/MinIO без лимитов). TutorCraft
в покое ≈ 3–4 ГБ вместе с самим k3s (~0.5–0.7 ГБ), потолок лимитов подов ≈ 5 ГБ, пик —
транскодирование видео.

- `available` в `free -h` ≥ 4.5 ГБ — можно запускать;
- 3.5–4.5 ГБ — запускать, но добавить swap 4 ГБ и следить за `k3s kubectl top pods -n tutorcraft` первые дни;
- < 3.5 ГБ — сначала увеличить RAM сервера. Иначе OOM-killer начнёт убивать процессы, в том числе confeek.

Кто на хосте терминирует HTTPS, покажет команда `ss` выше. Ниже основной вариант — nginx, для
Caddy — §4.6б. Если это Traefik в Docker, сначала нужно решить, кто держит 80/443: k3s со
встроенным Traefik на этом сервере не ставится (он выключен в `infra/deploy/k3s/config.yaml`).

## 3. Что уже подготовлено в репозитории

| Файл | Назначение |
|---|---|
| `infra/k8s/app/`, `infra/k8s/cluster/` | манифесты стека и кластерные объекты (namespace, PersistentVolume, RBAC) |
| `infra/deploy/bootstrap-ip.sh` | подготовка сервера: пользователь, `.env`, k3s, kubeconfig, vhost |
| `.env.prod.example` | шаблон `.env` для сервера |
| `infra/deploy/nginx/tutorcraft.conf` | vhost для host-nginx: `/` → web, `/ws` → notifier, 80 → 443 |
| `infra/deploy/nginx/cloudflare-realip.conf` | реальный IP посетителя из `CF-Connecting-IP` (только для vhost TutorCraft) |
| `infra/deploy/backup.sh` | ежедневный дамп PostgreSQL + MongoDB с ротацией |
| `.github/workflows/deploy-production.yml` | после зелёного `ci` на `main`: сборка 4 образов → рендер и проверка манифестов → `apply.sh` на сервере |

Отличия прода от dev-`docker-compose.yml`: нет SeaweedFS и Mailpit (внешние S3 и SMTP), нет
публикации Postgres/Mongo/Redis/Kafka наружу, у Kafka данные на томе и heap 512 МБ, у Mongo кэш
256 МБ, у core-api `-Xmx1g` вместо `MaxRAMPercentage=75`, демо-сиды выключены, `WORKER_CONCURRENCY=1`.

## 4. Пошагово

### 4.0. Предусловие — зелёный CI
Деплой запускается только после успешного `ci` на `main`. В `ci` входит и проверка
k8s-манифестов (job `k8s-manifests`).

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

### 4.3. Сервер: k3s, пользователь, каталог (от root)
Swap и bootstrap — как в [`deployment-ip.md` §3.1–3.2](deployment-ip.md#3-подготовка-сервера-один-раз-от-root).
Для другого сервера передайте его адрес: `TUTORCRAFT_IP=<ip> bash …/bootstrap-ip.sh …`.
Скрипт поставит vhost для IP, для домена замените его на `tutorcraft.conf` (§4.6).

**Почему отдельный пользователь.** Деплой идёт под ServiceAccount с правами только на namespace
`tutorcraft`. У confeek своя учётка и свой Docker, а `tutorcraft` в группе `docker` не состоит.

### 4.4. `.env` на сервере (под пользователем tutorcraft)
bootstrap уже создал `/opt/tutorcraft/.env` со случайными секретами. Поправить под домен:

```bash
sudo -iu tutorcraft nano /opt/tutorcraft/.env
```

- `WEB_ORIGIN` и `PUBLIC_BASE_URL` → `https://<домен>`, `COOKIE_SECURE=true`;
- `STORAGE_DRIVER=s3` и `S3_*` из §4.2;
- `SMTP_*`, OAuth, платежи.

Значения — без кавычек. `DATA_ENCRYPTION_KEY` и `JWT_SECRET` после запуска не менять: первый
шифрует секреты интеграций в БД, смена второго разлогинит всех. Сохранить `.env` в менеджер
паролей — без него бэкап БД бесполезен.

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

Variables → Actions: `PROD_WS_URL` = `wss://<домен>/ws`, `PROD_S3_ORIGIN` = origin бакета.
Обе вшиваются в бандл при сборке web.

### 4.6. TLS и reverse proxy

**Сертификат.** Проще всего — Cloudflare Origin CA (SSL/TLS → Origin Server → Create certificate, 15 лет):
сохранить в `/etc/nginx/ssl/<домен>.pem` и `.key` (`chmod 600` на ключ). Если для confeek используется
certbot — можно им же: `certbot certonly --webroot -w /var/www/certbot -d <домен> -d www.<домен>`
(vhost уже отдаёт `/.well-known/acme-challenge/` на 80-м порту) и поправить пути в конфиге.

**а) host-nginx** (от root):
```bash
cd /tmp/tutorcraft-infra/deploy/nginx
sed -i 's/tutorcraft\.example/<домен>/g' tutorcraft.conf cloudflare-realip.conf
cp cloudflare-realip.conf /etc/nginx/snippets/tutorcraft-cloudflare-realip.conf
cp tutorcraft.conf /etc/nginx/sites-available/tutorcraft.conf      # или /etc/nginx/conf.d/, если sites-* нет
ln -sfn /etc/nginx/sites-available/tutorcraft.conf /etc/nginx/sites-enabled/tutorcraft.conf
nginx -t && systemctl reload nginx     # nginx -t обязателен: ошибка уронит и confeek
```
До первого деплоя домен отдаёт 502 — это нормально.

**б) если на хосте Caddy** — добавить в Caddyfile:
```
<домен> {
    tls /etc/caddy/ssl/<домен>.pem /etc/caddy/ssl/<домен>.key
    reverse_proxy /ws 10.43.0.101:8090
    reverse_proxy 10.43.0.100:3000 {
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
GitHub → Actions → **Deploy to Production** → Run workflow → `all`. Workflow соберёт образы,
отрендерит и проверит манифесты, скопирует их в `/opt/tutorcraft` и запустит `infra/k8s/apply.sh`.
Тот поднимет хранилища, создаст топики Kafka и раскатит приложения, дожидаясь готовности каждого.
Дальше каждый push в `main` → `ci` → автодеплой.

### 4.8. Проверка
```bash
sudo -iu tutorcraft kubectl get pods                  # все Running/Ready, kafka-init — Completed
curl -sI https://<домен>/ | head -1                   # 200
k3s kubectl top pods -n tutorcraft; docker stats --no-stream   # память TutorCraft и confeek (от root)
curl -sI https://confeek.com/ | head -1               # соседа не задели
```
В браузере: регистрация/вход, загрузка файла, короткое видео (статус «готово» приходит по WebSocket),
письмо со сбросом пароля. Первого администратора создать через регистрацию (демо-сиды выключены).

Внешние сервисы, которым нужен новый домен:
- Google OAuth: Authorized JavaScript origins → `https://<домен>`;
- Telegram: `/setdomain` у @BotFather → домен (для Login Widget).

### 4.9. Бэкапы
```bash
crontab -e    # под пользователем tutorcraft
0 3 * * * cd /opt/tutorcraft && ./infra/deploy/backup.sh >> backups/backup.log 2>&1
```
Дампы в `/opt/tutorcraft/backups`, хранятся 14 дней (`BACKUP_RETENTION_DAYS`). Копировать их с сервера
(rclone в тот же S3 в отдельный бакет) — бэкап на том же диске не спасает от потери сервера.

## 5. Откат
```bash
sudo -iu tutorcraft
kubectl rollout undo deploy/core-api                  # и/или web, notifier, media-worker
```
Миграции Flyway только вперёд: если в откатываемом релизе была миграция, сначала восстановить БД из бэкапа.
Следующий деплой из `main` выставит свежий образ — чтобы закрепить откат, откатите коммит.

## 6. Чего не делать на общем сервере
- `docker system prune -a`, `docker volume prune` — удалят образы и тома confeek;
- включать в k3s встроенные Traefik/ServiceLB или NodePort/hostPort-сервисы — займут порты хоста или откроют их в интернет в обход Cloudflare;
- собирать образы на сервере — Maven и Next.js build съедят память продакшна confeek;
- запускать dev-`docker-compose.yml` на сервере — займёт порт 8080 у confeek;
- `systemctl reload nginx` без `nginx -t`.

## 7. Эксплуатация
```bash
sudo -iu tutorcraft
kubectl logs -f deploy/core-api --tail=200
kubectl rollout restart deploy/notifier
kubectl exec -it postgres-0 -- psql -U tutorcraft tutorcraft
kubectl port-forward svc/postgres 5434:5432      # DBeaver: SSH-туннель на сервер → 127.0.0.1:5434
```
Подробнее — [`deployment-ip.md` §9](deployment-ip.md#9-эксплуатация-и-откат).
