# Развёртывание TutorCraft на 91.149.179.186 (голый IP, HTTP, k3s)

Сценарий: приложение открывается по `http://91.149.179.186`, домена и TLS нет, файлы лежат на
диске сервера, почта — через обычный ящик с паролем приложения (§6.2). Сервер общий с confeek и
crm. Всё ниже сделано так, чтобы соседей не задеть.

TutorCraft работает в **k3s** (ADR-013): все восемь компонентов — от PostgreSQL до web — поды в
namespace `tutorcraft`. Соседи остаются в Docker, перед всеми по-прежнему стоит host-nginx.

Вариант с доменом, Cloudflare и внешним S3 описан отдельно в [`deployment.md`](deployment.md).

---

## 0. Что лежит в репозитории

| Файл | Что делает |
|---|---|
| `infra/k8s/app/` | манифесты стека (Kustomize): хранилища, Job с топиками Kafka, четыре сервиса, NetworkPolicy |
| `infra/k8s/cluster/` | namespace, PersistentVolume на `/opt/tutorcraft/data/*`, ServiceAccount деплоя. Применяет root один раз |
| `infra/k8s/render.sh` | рендер манифестов с тегом образа (CI) |
| `infra/k8s/apply.sh` | применение на сервере: секреты из `.env` → хранилища → топики → приложения |
| `infra/deploy/k3s/` | `config.yaml` k3s и правило `tutorcraft-k3s-guard`, закрывающее порты k3s снаружи |
| `infra/deploy/bootstrap-ip.sh` | одноразовая подготовка сервера: пользователь, `.env`, k3s, kubeconfig, vhost |
| `infra/deploy/nginx/tutorcraft-ip.conf` | vhost на IP: лимиты запросов, блокировка сканеров, `/storage/` → core-api |
| `infra/deploy/backup.sh` | ежедневный дамп PostgreSQL и MongoDB через `kubectl exec` |

### Как устроено

```
посетитель → host-nginx :80 ─┬─ /          → 10.43.0.100:3000  (Service web)
                             ├─ /ws        → 10.43.0.101:8090  (Service notifier)
                             └─ /storage/  → 10.43.0.102:8080  (Service core-api)
                                    k3s, namespace tutorcraft:
                                    web → core-api → postgres-0, mongo-0, redis, kafka-0
                                    media-worker, notifier ↔ kafka-0
```

- **Наружу не открыт ни один порт приложения.** nginx ходит в закреплённые ClusterIP сервисов:
  с хоста они доступны через правила kube-proxy, из интернета — нет. Это строже прежней схемы
  с `127.0.0.1:3100` и подобными портами.
- **Встроенные Traefik и ServiceLB выключены**: 80/443 остаются за host-nginx и соседями.
- **Порты самого k3s** (API 6443, kubelet 10250, kube-proxy 10256) слушают все интерфейсы. Снаружи
  их закрывает точечное правило `tutorcraft-k3s-guard` (systemd-юнит), UFW не трогается.
- **Данные** — обычные каталоги `/opt/tutorcraft/data/{postgres,mongo,kafka,files}`, подключённые
  как PersistentVolume с `Retain`. Удаление подов, PVC и даже namespace их не стирает.
- **Деплой** идёт под ServiceAccount `deployer` с ролью `admin` только в namespace `tutorcraft`.
  Раньше пользователь `tutorcraft` был в группе `docker`, а это root на всём сервере. Pod Security
  `baseline` не даёт обойти ограничение через hostPath или privileged-под.
- **Порядок старта** вместо `depends_on`: `apply.sh` поднимает хранилища, ждёт Job `kafka-init`
  и только потом приложения. После перезагрузки сервера init-контейнеры ждут свои зависимости по TCP.

---

## 1. Состояние сервера (замер 2026-09-28)

| Проверка | Результат |
|---|---|
| Кто на :80 и :443 | host-nginx 1.24.0 (Ubuntu) |
| Занято соседями | `:8080` confeek (`spruzhuk_app`), `:8082` `crm-crm-1`, `:5433` `spruzhuk_db` |
| Память | 7.9 ГБ всего, 3.2 ГБ занято, **4.6 ГБ available, swap = 0** |
| Соседей на хосте | **два**: confeek (`spruzhuk_*`) и crm |

**Память.** Сумма лимитов подов ≈ 5.0 ГБ (postgres и mongo по 512M, redis 192M, kafka 768M,
core-api 1.5G, media-worker 768M, notifier 256M, web 512M) — столько же, сколько было в compose.
Сверху добавляется сам k3s: ~0.5–0.7 ГБ на API-сервер, kubelet, containerd, coredns и
metrics-server. В покое стек займёт ~3 ГБ против ~2.5 ГБ в compose. Лимит — потолок, а не резерв.
При нулевом swap любой пик (транскодирование видео + прогрев JVM) сразу упирается в OOM-killer,
а тот выбирает жертву по объёму памяти, а не по владельцу: упасть может и confeek. **Swap из §3.1
обязателен.**

Если состав хоста изменится, перепроверить:

```bash
ss -ltnp | grep -E ':(80|443|6443|10250)\b'
free -h; df -h /var/lib/rancher /opt/tutorcraft; nproc
ip -4 route | grep -E '10\.4[23]\.'     # пусто: сети k3s не пересекаются с Docker соседей
docker ps --format '{{.Names}}\t{{.Ports}}'
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

### 3.1. Swap — сделать ДО установки k3s

```bash
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab      # переживёт перезагрузку
sysctl -w vm.swappiness=10 && echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
free -h                                               # Swap: 4.0Gi
```

`swappiness=10`: не гонять в swap то, что активно используется. Swap — подушка на пике, а не
штатный режим. k3s со swap работает: `fail-swap-on=false` у него по умолчанию.

Откат: `swapoff /swapfile && rm /swapfile` и убрать строку из `/etc/fstab`.

### 3.2. Bootstrap: пользователь, `.env`, k3s, vhost

Скопируйте на сервер **весь каталог `infra/`** и публичный ключ деплоя:

```bash
scp -r infra root@91.149.179.186:/tmp/tutorcraft-infra
```

```bash
scp ~/.ssh/tutorcraft_deploy.pub root@91.149.179.186:/tmp/
```

На сервере:

```bash
bash /tmp/tutorcraft-infra/deploy/bootstrap-ip.sh /tmp/tutorcraft_deploy.pub
```

Скрипт идемпотентен. По шагам он:

1. заводит пользователя `tutorcraft` **без** группы `docker`;
2. создаёт `/opt/tutorcraft`, каталоги данных и `.env` со случайными секретами;
3. ставит `tutorcraft-k3s-guard`, затем k3s (канал stable, конфиг из `infra/deploy/k3s/config.yaml`);
4. применяет namespace, PersistentVolume и RBAC деплоя;
5. собирает `/home/tutorcraft/.kube/config` с правами только на namespace `tutorcraft`;
6. ставит vhost nginx и перезагружает его **только после успешного `nginx -t`**.

Существующие `.env` и k3s он не трогает. Docker, контейнеры и конфиги соседей тоже.

Версию k3s можно закрепить: `INSTALL_K3S_VERSION=v1.xx.y+k3s1 bash …/bootstrap-ip.sh …`.

Пароль администратора скрипт на экран не печатает, чтобы тот не осел в истории терминала:

```bash
sudo grep ADMIN_PASSWORD /opt/tutorcraft/.env
```

Сохраните весь `/opt/tutorcraft/.env` в менеджер паролей: `DATA_ENCRYPTION_KEY` расшифровывает
секреты интеграций в БД, без него резервная копия базы бесполезна. Менять его после первого
запуска нельзя. Смена `JWT_SECRET` разлогинит всех.

Проверка:

```bash
k3s kubectl get nodes                                  # Ready
systemctl is-active tutorcraft-k3s-guard               # active
iptables -S INPUT | grep tutorcraft-k3s-guard          # правило DROP на 6443,10250,10256
sudo -iu tutorcraft kubectl get pods                   # No resources found — доступ есть
sudo -iu tutorcraft kubectl get pods -n kube-system    # Forbidden — и это правильно
```

### 3.3. Переезд с docker compose (один раз, если стек уже работал в compose)

Порядок важен: базы нельзя восстанавливать поверх работающего core-api. Простой — от остановки
compose (шаг 1) до проверки (шаг 8), обычно 15–30 минут.

> После мержа ветки с k3s автодеплой упадёт на шаге `apply.sh` с сообщением «kubectl не найден»:
> k3s ещё не установлен. Это ожидаемо, compose-стек он не трогает. Но `scp` в том же деплое
> заменит `infra/deploy/backup.sh` на версию для k3s, и ночной бэкап compose-стека перестанет
> работать. Проводите переезд в тот же день.

**1. Остановить запись и дождаться воркеров** (от root):

```bash
docker stop tutorcraft_web tutorcraft_core_api
docker logs --since 5m tutorcraft_media_worker   # дождаться конца транскодирования, если оно идёт
docker stop tutorcraft_media_worker tutorcraft_notifier
```

Kafka не переносится. Всё, что core-api не успел опубликовать, лежит в таблице `outbox` в
PostgreSQL (ADR-005), и `OutboxRelay` отправит это в новый брокер после переезда.

**2. Снять дампы:**

```bash
install -d -m 750 -o tutorcraft -g tutorcraft /opt/tutorcraft/migration
cd /opt/tutorcraft/migration
docker exec tutorcraft_postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner' > pg.dump
docker exec tutorcraft_mongo mongodump --quiet --archive --gzip > mongo.archive.gz
chown tutorcraft:tutorcraft pg.dump mongo.archive.gz
ls -lh                                           # оба файла не нулевые
```

**3. Остановить старый стек, не удаляя тома** (они — путь отката):

```bash
cd /opt/tutorcraft
docker compose -f docker-compose.prod.yml -f docker-compose.ip.yml stop
```

Compose-файлы на сервере остаются: `scp` из деплоя файлы не удаляет.

**4. Bootstrap** (§3.2). vhost nginx переключится на ClusterIP, и до шага 6 сайт отдаёт 502.

**5. Перенести загруженные файлы:**

```bash
src=$(docker volume inspect -f '{{.Mountpoint}}' tutorcraft_file-storage)
cp -a "$src/." /opt/tutorcraft/data/files/
chown -R 10001:10001 /opt/tutorcraft/data/files
du -sh "$src" /opt/tutorcraft/data/files         # размеры совпадают
```

**6. Первый деплой:** GitHub → Actions → **Deploy to Production** → Run workflow → `all`.
Поды поднимутся на пустых базах, core-api прогонит Flyway и создаст администратора. Это
временно: шаг 7 заменит базы дампами.

**7. Восстановить базы** (под `tutorcraft`):

```bash
sudo -iu tutorcraft
k() { kubectl -n tutorcraft "$@"; }
k scale deployment core-api media-worker notifier web --replicas=0

k exec postgres-0 -- sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
k exec -i postgres-0 -- sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' \
  < /opt/tutorcraft/migration/pg.dump
# tutorcraft — значение MONGO_DB из .env
k exec -i mongo-0 -- mongorestore --quiet --archive --gzip --drop --nsInclude='tutorcraft.*' \
  < /opt/tutorcraft/migration/mongo.archive.gz

cd /opt/tutorcraft && infra/k8s/apply.sh     # вернёт replicas: 1 и дождётся раскатки
```

**8. Проверить:** вход под существующей учётной записью, открыть курс с файлом и с видео (HLS),
загрузить новый файл. Дальше — проверки из §6.

**9. Уборка — через неделю стабильной работы** (от root):

```bash
cd /opt/tutorcraft
docker compose -f docker-compose.prod.yml -f docker-compose.ip.yml down      # БЕЗ -v
docker volume rm tutorcraft_pg-data tutorcraft_mongo-data tutorcraft_kafka-data \
                 tutorcraft_media-scratch tutorcraft_file-storage            # только свои тома, по именам
docker image ls 'ghcr.io/prokhor77/tutorcraft-*' -q | xargs -r docker image rm
rm -f docker-compose.prod.yml docker-compose.ip.yml infra/kafka/create-topics.sh
gpasswd -d tutorcraft docker && rm -f /home/tutorcraft/.docker/config.json
rm -rf /opt/tutorcraft/migration
```

**Откат, если на шагах 4–8 что-то пошло не так:** k3s останавливается, старый стек поднимается
с теми же томами. Данные, записанные уже в k3s, при этом теряются.

```bash
systemctl stop k3s && /usr/local/bin/k3s-killall.sh        # поды и сетевые правила k3s
cd /opt/tutorcraft
docker compose -f docker-compose.prod.yml -f docker-compose.ip.yml start
```

Затем вернуть vhost со старыми upstream `127.0.0.1:3100/8190/3180`. На сервере git нет, поэтому
файл берётся из локального клона, из коммита до ADR-013:

```bash
git show <коммит-до-k3s>:infra/deploy/nginx/tutorcraft-ip.conf \
  | ssh root@91.149.179.186 'cat > /etc/nginx/sites-available/tutorcraft.conf && nginx -t && systemctl reload nginx'
```

`k3s-killall.sh` убирает из iptables только цепочки `KUBE-*`, `CNI-*` и flannel, правила Docker
соседей остаются. Полное удаление k3s — `/usr/local/bin/k3s-uninstall.sh`. Каталоги
`/opt/tutorcraft/data` он не трогает.

---

## 4. Настройки GitHub (Settings → Secrets and variables → Actions)

**Secrets** — те же, что и до k3s:

| Секрет | Значение |
|---|---|
| `PROD_HOST` | `91.149.179.186` |
| `PROD_USER` | `tutorcraft` |
| `PROD_SSH_KEY` | приватный ключ `tutorcraft_deploy` целиком |
| `PROD_PATH` | `/opt/tutorcraft` |
| `GHCR_PULL_USER` | `Prokhor77` |
| `GHCR_PULL_TOKEN` | classic PAT **только** с правом `read:packages`. Из него `apply.sh` собирает imagePullSecret `ghcr` |

**Variables:**

| Переменная | Значение |
|---|---|
| `PROD_WS_URL` | `ws://91.149.179.186/ws` |
| `PROD_S3_ORIGIN` | `http://91.149.179.186:9000` |

Обе вшиваются в JS-бандл при сборке образа и попадают в CSP. Неверная схема в `PROD_WS_URL` —
не будет живых уведомлений.

---

## 5. Почему автодеплой раньше не мог сработать

`deploy-production.yml` стартует по `workflow_run` успешного `ci`. Зелёным `ci` быть не мог:

- job `e2e` сверял живую спецификацию с `docs/api/openapi.json`, которого в репозитории нет;
- он же поднимал весь compose-стек и гонял Playwright — тяжёлый и нестабильный шаг гейтил выкладку.

Теперь `e2e` живёт в отдельном workflow (`.github/workflows/e2e.yml`, по расписанию и вручную),
а сверка OpenAPI при отсутствии базового файла не валит сборку, а публикует сгенерированную
спецификацию артефактом `openapi-spec` — скачать и закоммитить в `docs/api/openapi.json`.

---

## 6. Деплой и проверка

GitHub → Actions → **Deploy to Production** → Run workflow → `all`. Дальше каждый push в `main`
→ зелёный `ci` → автодеплой.

Что делает workflow:

1. собирает четыре образа в GHCR (`latest` и `sha-<commit>`);
2. рендерит манифесты с тегом `sha-<commit>` и проверяет их kubeconform;
3. копирует `tutorcraft.rendered.yaml`, `apply.sh`, `backup.sh` и vhost в `/opt/tutorcraft`;
4. запускает `infra/k8s/apply.sh`: Secret из `.env` → хранилища → `kafka-init` → приложения, и
   ждёт каждую раскатку. При провале печатает `describe` и логи упавшего объекта.

core-api раскатывается с **простоем ~1–2 минуты** (стратегия `Recreate`): два пода по 1.5 ГБ
одновременно этот сервер не выдержит. web и notifier обновляются без простоя.

Проверка (под `tutorcraft`):

```bash
sudo -iu tutorcraft
kubectl get pods                    # всё Running/Ready, kafka-init — Completed
curl -sI http://91.149.179.186/ | head -1                       # 200
curl -sI http://91.149.179.186/api/v1/openapi.json | head -1    # 404 — спецификация закрыта
curl -sI https://confeek.com/ | head -1                         # соседа не задели
```

Снаружи (со своей машины) порты k3s должны быть закрыты:

```bash
nc -zv -w 3 91.149.179.186 6443
```

То же для 10250: обе команды должны завершиться таймаутом.

Первые дни поглядывайте на память (от root: у `deployer` нет прав на метрики узла):

```bash
free -h; k3s kubectl top node; k3s kubectl top pods -n tutorcraft
dmesg -T | grep -i 'killed process' | tail          # пусто = OOM-killer не срабатывал
```

Если `available` регулярно уходит ниже 1 ГБ — снижать `core-api` (лимит **и** `-Xmx` в
`infra/k8s/app/core-api.yaml`, они меняются вместе) либо добавлять RAM.

В браузере: `http://91.149.179.186` → вход под `ADMIN_EMAIL` / `ADMIN_PASSWORD` → создание курса.

### 6.1. Файлы на диске сервера

`STORAGE_DRIVER=local`: загруженные файлы и HLS лежат в `/opt/tutorcraft/data/files`. Это том
`files-data`, общий для core-api и media-worker; оба пишут под UID 10001. Браузер грузит и
скачивает их через core-api по подписанным ссылкам `/storage/{key}?…&sig=…` (HMAC от
`JWT_SECRET`). Публичен только префикс `hls/`. nginx проводит `/storage/` прямо в core-api,
минуя BFF Next.js: через него 2-гигабайтные видео упёрлись бы в лимиты и таймауты Node.

Переезд на S3 — `STORAGE_DRIVER=s3` и `S3_*` в `.env`, затем `infra/k8s/apply.sh`.

### 6.2. Почта: письма-приглашения и сброс пароля

Без SMTP notifier пропускает email-канал (`skipped: email is not configured`): приглашение в
курс создаёт аккаунт, но письмо не уходит — ссылку активации репетитор пересылает сам. Чтобы
письма приходили, достаточно почтового ящика с паролем приложения (домен не нужен).

| Провайдер | `SMTP_HOST` | `SMTP_PORT` | `SMTP_USERNAME` / `SMTP_PASSWORD` | Лимит |
|---|---|---|---|---|
| Gmail | `smtp.gmail.com` | `465` | адрес ящика / [пароль приложения](https://myaccount.google.com/apppasswords) (нужна 2FA) | ~500 писем/сутки |
| Яндекс | `smtp.yandex.ru` | `465` | адрес ящика / пароль приложения (Яндекс ID → Безопасность → Пароли приложений → «Почта») | ~500 писем/сутки |
| Mail.ru | `smtp.mail.ru` | `465` | адрес ящика / пароль для внешних приложений | — |

`SMTP_FROM` — тот же адрес, что и `SMTP_USERNAME`, с именем: `TutorCraft <you@gmail.com>`.
Пароль приложения — это ключ от ящика. Храните его только в `/opt/tutorcraft/.env` (и в
менеджере паролей), не в репозитории и не в GitHub Variables.

```bash
sudo -iu tutorcraft
nano /opt/tutorcraft/.env          # SMTP_HOST/PORT/USERNAME/PASSWORD/FROM, без кавычек
cd /opt/tutorcraft && infra/k8s/apply.sh     # новый .env → Secret → перезапуск подов
kubectl logs deploy/notifier | grep 'notifier started'   # должно быть "email":true
```

Проверка: пригласите в курс свой второй адрес. Если письма нет, `kubectl logs deploy/notifier`
покажет код ответа SMTP: `535` — неверный пароль приложения, `connect: … timeout` — хостер
закрыл исходящий порт (попробуйте `587`). Ссылки в письме ведут на `http://91.149.179.186`,
поэтому первое письмо почтовик может положить в «Спам» — отметьте «Не спам». Когда появится
домен, лучше перейти на транзакционный сервис (Unisender Go, Brevo, Postmark) с SPF/DKIM на домене.

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
  (пробы k8s ходят в собственный `HealthController`, минуя web-слой Actuator);
- `X-Forwarded-For` принимается только от loopback и внутренних сетей (`10/8` покрывает поды и
  адрес узла k3s). Иначе клиент подделал бы заголовок и обошёл троттлинг входа, подставляя на
  каждую попытку новый IP;
- CSP `connect-src` сужен до своего origin, сокета и S3;
- `console.*` вырезается из клиентского бандла (кроме `console.error`), source maps не публикуются.

**На уровне k3s:**

- порты приложения не открыты ни на одном интерфейсе: nginx → ClusterIP;
- API, kubelet и kube-proxy закрыты снаружи правилом `tutorcraft-k3s-guard`, flannel в режиме
  `host-gw` (без UDP 8472 наружу);
- деплой и бэкап — под ServiceAccount с правами только на namespace, без группы `docker`;
- Pod Security `baseline` на namespace: никаких hostPath, privileged, hostNetwork в подах;
- `seccompProfile: RuntimeDefault` у всех подов, `allowPrivilegeEscalation: false` у всех
  контейнеров, `capabilities: drop [ALL]` у приложений, Redis, Kafka и init-контейнеров
  (postgres и mongo без них не сменят пользователя на старте);
- NetworkPolicy: PostgreSQL и Redis принимают только core-api, Kafka — только сервисы и
  `kafka-init`;
- Secret `tutorcraft-env` (весь `.env`) в базе k3s зашифрован (`secrets-encryption`);
- токен ServiceAccount не монтируется ни в один под (`automountServiceAccountToken: false`);
- без подписи отдаётся только префикс `hls/`, ответы `/storage/` идут с CSP `default-src 'none'`.

### Что защитой **не** закрыто

1. **Трафик идёт открытым текстом.** Это прямое следствие выбора «голый IP без TLS»: пароли,
   access-токен и refresh-cookie видны любому на пути (Wi-Fi, провайдер, хостер). `COOKIE_SECURE`
   вынужденно `false`, иначе браузер выбросит cookie и вход будет слетать каждые 15 минут.
   Перед тем как пускать учеников, нужен домен с TLS.
2. **`script-src` содержит `'unsafe-inline'`** — inline-бутстрап Next.js. Nonce-based CSP числится
   в TODO `apps/web/README.md`.
3. **root на сервере по-прежнему видит всё**, включая `/opt/tutorcraft/.env` и kubeconfig
   администратора k3s `/etc/rancher/k3s/k3s.yaml`. Изоляция защищает от утечки ключа деплоя,
   а не от root.

### Опционально: UFW и fail2ban

Сервер общий с двумя чужими приложениями, поэтому автоматически это не включается: неверное
правило отрежет confeek или crm. Наружу TutorCraft нужен только порт 80.

fail2ban по логам nginx (`/var/log/nginx/tutorcraft.access.log`) имеет смысл ставить после того,
как вы увидите реальный профиль атак — до этого лимитов nginx достаточно.

---

## 8. Обфускация: что реально сделано и почему не больше

- **Java (core-api) и Go (воркеры) на клиент не уходят вообще.** Их байткод и бинарники лежат
  только в образах на сервере. Обфусцировать их — значит защищаться от того, у кого уже есть root
  на хосте. ProGuard поверх Spring Boot вдобавок ломает рефлексию (Jackson, маппинг строк JDBC
  по именам, имена бинов): риск падения прода ради нулевой выгоды.
- **Go-бинарники собираются с `-trimpath -ldflags="-s -w"`**: таблицы символов и пути сборки вырезаны.
- **Клиентский JS — единственное, что действительно отдаётся пользователю.** Next.js в production
  минифицирует и манглит имена. Дополнительно отключены source maps и вырезан `console.*`.
- **Убрана информация о внутреннем устройстве**: OpenAPI-спецификация, Swagger UI, `/actuator`,
  версия nginx, стектрейсы в ответах.

---

## 9. Эксплуатация и откат

Под пользователем `tutorcraft` (`sudo -iu tutorcraft`), namespace по умолчанию — `tutorcraft`:

```bash
kubectl get pods
kubectl logs -f deploy/core-api --tail=200
kubectl logs statefulset/kafka --tail=100
kubectl rollout restart deploy/notifier
kubectl exec -it postgres-0 -- psql -U tutorcraft tutorcraft
kubectl port-forward svc/postgres 5434:5432       # DBeaver: SSH-туннель на сервер → 127.0.0.1:5434
```

**Откат приложения** на предыдущий образ:

```bash
kubectl rollout undo deploy/core-api               # и/или web, notifier, media-worker
kubectl rollout history deploy/core-api            # хранится 5 ревизий
```

Следующий деплой из `main` снова выставит свежий образ. Чтобы закрепить откат, откатите коммит
в `main`. Миграции Flyway только вперёд: если в откатываемом релизе была миграция, сначала
восстановить БД из бэкапа.

**Правка `.env`** вступает в силу после `cd /opt/tutorcraft && infra/k8s/apply.sh`: поды с
изменившимся `.env` перезапустятся сами (хэш файла в аннотации пода).

**Бэкапы** (под `tutorcraft`):

```bash
crontab -e
0 3 * * * cd /opt/tutorcraft && ./infra/deploy/backup.sh >> backups/backup.log 2>&1
```

Дампы PostgreSQL и MongoDB лежат в `/opt/tutorcraft/backups` 14 дней. Каталог файлов
`/opt/tutorcraft/data/files` скрипт не копирует: снимайте его отдельно на другой сервер или в
S3. Копия на том же диске от потери сервера не спасает.

**Восстановление из дампа** — как в §3.3, шаг 7, только с файлами из `backups/`. Для
PostgreSQL дамп в текстовом формате:
`gunzip -c backups/postgres_….sql.gz | kubectl exec -i postgres-0 -- sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`.

**От root** — сам k3s:

```bash
systemctl status k3s; journalctl -u k3s --since '1 hour ago'
k3s kubectl get pods -A
k3s crictl images | grep tutorcraft                # образы в containerd k3s, не в Docker
```

Неиспользуемые образы k3s удаляет сам (image GC kubelet при заполнении диска на 85 %).

### Чего не делать

- `docker system prune -a`, `docker volume prune`: снесут образы и тома соседей;
- `kubectl delete namespace tutorcraft` или удаление PVC: данные на `Retain`-томах уцелеют, но
  стек ляжет, и тома придётся освобождать от root (`claimRef`);
- менять `cluster-cidr` и `service-cidr` в `/etc/rancher/k3s/config.yaml` после установки: на них
  завязаны ClusterIP в nginx, правило guard и NetworkPolicy;
- запускать `k3s-killall.sh` или `k3s-uninstall.sh` без нужды: это остановка всего стека;
- собирать образы на сервере: Maven и Next.js съедят память соседнего прода;
- `systemctl reload nginx` без `nginx -t`.
