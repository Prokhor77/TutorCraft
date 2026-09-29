#!/usr/bin/env bash
# Одноразовая подготовка сервера под TutorCraft (сценарий «голый IP, HTTP»).
# Запускать от root НА СЕРВЕРЕ:  bash bootstrap-ip.sh <путь-к-публичному-ключу-деплоя>
#
# Что делает:
#   1. заводит системного пользователя tutorcraft и кладёт ему ключ деплоя;
#   2. создаёт /opt/tutorcraft и .env со случайными секретами;
#   3. ставит vhost nginx и перезагружает его — только после `nginx -t`.
#
# Чего НЕ делает (намеренно, сервер общий с confeek):
#   • не трогает контейнеры, тома, образы и конфиги соседа;
#   • не включает и не перенастраивает UFW/fail2ban — это отдельное решение, см. docs/deployment-ip.md;
#   • не удаляет ничего: повторный запуск безопасен, существующий .env не перезаписывается.
set -euo pipefail

IP="91.149.179.186"
APP_USER="tutorcraft"
APP_DIR="/opt/tutorcraft"
PUBKEY_PATH="${1:-}"

die() { echo "ОШИБКА: $*" >&2; exit 1; }
info() { echo "==> $*"; }

[[ $EUID -eq 0 ]] || die "запускать от root"
[[ -n "$PUBKEY_PATH" && -f "$PUBKEY_PATH" ]] || die "укажите файл с публичным ключом: bash $0 tutorcraft_deploy.pub"
command -v docker >/dev/null || die "docker не установлен"
docker compose version >/dev/null 2>&1 || die "нужен docker compose v2 (плагин), а не docker-compose"
command -v openssl >/dev/null || die "нужен openssl"

# --- 1. Пользователь -------------------------------------------------------------------------
# Отдельный пользователь нужен из-за ~/.docker/config.json: `docker login ghcr.io` хранит одну
# учётку на реестр, и логин под аккаунтом TutorCraft в пользователе confeek сломал бы pull соседу.
# Изоляцией по безопасности это не является — группа docker равносильна root.
if id "$APP_USER" >/dev/null 2>&1; then
  info "пользователь $APP_USER уже есть"
else
  info "создаю пользователя $APP_USER"
  adduser --disabled-password --gecos "" "$APP_USER"
fi
usermod -aG docker "$APP_USER"

install -d -m 700 -o "$APP_USER" -g "$APP_USER" "/home/$APP_USER/.ssh"
touch "/home/$APP_USER/.ssh/authorized_keys"
if ! grep -qxF "$(cat "$PUBKEY_PATH")" "/home/$APP_USER/.ssh/authorized_keys"; then
  cat "$PUBKEY_PATH" >> "/home/$APP_USER/.ssh/authorized_keys"
  info "ключ деплоя добавлен"
fi
chown "$APP_USER:$APP_USER" "/home/$APP_USER/.ssh/authorized_keys"
chmod 600 "/home/$APP_USER/.ssh/authorized_keys"

# --- 2. Каталог и .env -----------------------------------------------------------------------
install -d -m 750 -o "$APP_USER" -g "$APP_USER" "$APP_DIR"
install -d -m 750 -o "$APP_USER" -g "$APP_USER" "$APP_DIR/backups"

rnd() { openssl rand -base64 "$1" | tr -d '\n/+=' | cut -c1-"$2"; }

if [[ -f "$APP_DIR/.env" ]]; then
  info ".env уже есть — не трогаю (секреты менять нельзя: DATA_ENCRYPTION_KEY расшифровывает БД)"
else
  info "генерирую $APP_DIR/.env"
  umask 077
  cat > "$APP_DIR/.env" <<ENVFILE
WEB_ORIGIN=http://${IP}
PUBLIC_BASE_URL=http://${IP}
COOKIE_SECURE=false
CORE_PROFILE=prod

POSTGRES_DB=tutorcraft
POSTGRES_USER=tutorcraft
POSTGRES_PASSWORD=$(rnd 24 24)

MONGO_DB=tutorcraft
REDIS_PASSWORD=$(rnd 24 24)

S3_ENDPOINT=http://s3:8333
S3_PUBLIC_ENDPOINT=http://${IP}:9000
S3_REGION=us-east-1
S3_BUCKET=tutorcraft
S3_ACCESS_KEY=tc$(rnd 12 14)
S3_SECRET_KEY=$(rnd 32 32)

KAFKA_TOPIC_PREFIX=tc

JWT_SECRET=$(rnd 48 48)
DATA_ENCRYPTION_KEY=$(openssl rand -base64 32)

ADMIN_EMAIL=admin@tutorcraft.local
ADMIN_PASSWORD=Admin-$(rnd 18 18)7

GOOGLE_CLIENT_ID=
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=

PAYMENT_PROVIDER=fake

SMTP_HOST=
SMTP_PORT=587
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=TutorCraft <no-reply@tutorcraft.local>

LOG_LEVEL=info
MAX_VIDEO_BYTES=2147483648
TRANSCODE_TIMEOUT=30m
WORKER_CONCURRENCY=1
NOTIFIER_CONCURRENCY=4
TELEGRAM_POLLING_ENABLED=false

SEED_DEMO_DATA=false
SEED_DEMO_PASSWORD=
ENVFILE
  chown "$APP_USER:$APP_USER" "$APP_DIR/.env"
  chmod 600 "$APP_DIR/.env"
fi

# --- 3. nginx --------------------------------------------------------------------------------
# Сначала проверяем, не занят ли IP чужим vhost'ом: если у confeek стоит default_server,
# точное совпадение server_name у нас всё равно выиграет, но знать об этом полезно.
if ! command -v nginx >/dev/null; then
  info "nginx на хосте не найден — пропускаю установку vhost, поставьте его вручную"
else
  SNIPPETS=/etc/nginx/snippets
  install -d -m 755 "$SNIPPETS"
  [[ -f tutorcraft-proxy.conf ]] || die "положите рядом tutorcraft-proxy.conf и tutorcraft-ip.conf из infra/deploy/nginx/"
  [[ -f tutorcraft-ip.conf ]] || die "положите рядом tutorcraft-ip.conf из infra/deploy/nginx/"
  cp tutorcraft-proxy.conf "$SNIPPETS/tutorcraft-proxy.conf"

  if [[ -d /etc/nginx/sites-available ]]; then
    cp tutorcraft-ip.conf /etc/nginx/sites-available/tutorcraft.conf
    ln -sfn /etc/nginx/sites-available/tutorcraft.conf /etc/nginx/sites-enabled/tutorcraft.conf
  else
    cp tutorcraft-ip.conf /etc/nginx/conf.d/tutorcraft.conf
  fi

  # nginx -t обязателен: битый конфиг при reload уронит и confeek.
  if nginx -t; then
    systemctl reload nginx
    info "nginx перезагружен"
  else
    info "nginx -t не прошёл — откатываю vhost, confeek не затронут"
    rm -f /etc/nginx/sites-enabled/tutorcraft.conf /etc/nginx/conf.d/tutorcraft.conf
    die "проверьте вывод nginx -t выше"
  fi
fi

cat <<DONE

Готово. Дальше:
  1. Приватную часть ключа деплоя положить в секрет GitHub PROD_SSH_KEY.
  2. Пароль администратора:  sudo grep ADMIN_PASSWORD $APP_DIR/.env
     (на экран не печатаю, чтобы не осел в истории терминала и в логах CI)
  3. Скопировать $APP_DIR/.env в менеджер паролей — без него бэкап БД бесполезен.
  4. Запустить деплой: GitHub → Actions → Deploy to Production → Run workflow.

До первого деплоя http://${IP} отдаёт 502 — это нормально.
DONE
