#!/usr/bin/env bash
# Одноразовая подготовка сервера под TutorCraft на k3s (сценарий «голый IP, HTTP», ADR-013).
# Запускать от root НА СЕРВЕРЕ из скопированного каталога infra/ репозитория:
#   bash /tmp/tutorcraft-infra/deploy/bootstrap-ip.sh /tmp/tutorcraft_deploy.pub
#
# Что делает:
#   1. заводит системного пользователя tutorcraft и кладёт ему ключ деплоя;
#   2. создаёт /opt/tutorcraft, каталоги данных и .env со случайными секретами;
#   3. ставит правило tutorcraft-k3s-guard (порты k3s закрыты снаружи) и сам k3s;
#   4. применяет namespace, PersistentVolume и RBAC деплоя (infra/k8s/cluster/);
#   5. собирает пользователю tutorcraft kubeconfig с правами только на namespace tutorcraft;
#   6. ставит vhost nginx и перезагружает его, только если прошёл `nginx -t`.
#
# Чего НЕ делает (намеренно, сервер общий с confeek и crm):
#   • не трогает Docker, контейнеры, тома и конфиги соседей;
#   • не включает и не перенастраивает UFW/fail2ban (docs/deployment-ip.md §7);
#   • ничего не удаляет: повторный запуск безопасен, существующий .env не перезаписывается,
#     установленный k3s не переустанавливается.
set -euo pipefail

# Адрес для WEB_ORIGIN/PUBLIC_BASE_URL в новом .env. Для другого сервера: TUTORCRAFT_IP=… bash bootstrap-ip.sh …
IP="${TUTORCRAFT_IP:-91.149.179.186}"
APP_USER="tutorcraft"
APP_DIR="/opt/tutorcraft"
DATA_DIR="$APP_DIR/data"
PUBKEY_PATH="${1:-}"
INFRA="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

die() { echo "ОШИБКА: $*" >&2; exit 1; }
info() { echo "==> $*"; }

[[ $EUID -eq 0 ]] || die "запускать от root"
[[ -n "$PUBKEY_PATH" && -f "$PUBKEY_PATH" ]] || die "укажите файл с публичным ключом: bash $0 tutorcraft_deploy.pub"
command -v openssl >/dev/null || die "нужен openssl"
command -v curl >/dev/null || die "нужен curl"
for f in k8s/cluster/namespace.yaml deploy/k3s/config.yaml deploy/k3s/tutorcraft-k3s-guard.sh \
         deploy/nginx/tutorcraft-ip.conf deploy/nginx/tutorcraft-proxy.conf; do
  [[ -f "$INFRA/$f" ]] || die "нет $INFRA/$f: скопируйте на сервер весь каталог infra/ репозитория"
done

# --- 0. Предпроверки -------------------------------------------------------------------------
# Сети k3s не должны пересекаться с сетями Docker соседей, иначе пропадёт связь у кого-то из них.
if ! command -v k3s >/dev/null && ip -4 route | grep -qE '^10\.4[23]\.'; then
  ip -4 route | grep -E '^10\.4[23]\.' >&2
  die "маршруты в 10.42/16 или 10.43/16 уже заняты: поменяйте cluster-cidr и service-cidr в infra/deploy/k3s/config.yaml, ClusterIP в infra/k8s/app/*.yaml и upstream в nginx"
fi
avail_mb=$(awk '/MemAvailable/ { print int($2 / 1024) }' /proc/meminfo)
if (( avail_mb < 3500 )); then
  info "ВНИМАНИЕ: свободно ${avail_mb} МБ RAM. Стеку с k3s нужно ~3 ГБ в покое. Проверьте swap (§3.1) и остановлен ли старый compose-стек"
fi

# --- 1. Пользователь -------------------------------------------------------------------------
# В группу docker пользователь больше не добавляется: деплой идёт через kubectl с правами только
# на свой namespace. Если он остался в группе от compose-эпохи, убрать после миграции (§3.3).
if id "$APP_USER" >/dev/null 2>&1; then
  info "пользователь $APP_USER уже есть"
else
  info "создаю пользователя $APP_USER"
  adduser --disabled-password --gecos "" "$APP_USER"
fi

install -d -m 700 -o "$APP_USER" -g "$APP_USER" "/home/$APP_USER/.ssh"
touch "/home/$APP_USER/.ssh/authorized_keys"
if ! grep -qxF "$(cat "$PUBKEY_PATH")" "/home/$APP_USER/.ssh/authorized_keys"; then
  cat "$PUBKEY_PATH" >> "/home/$APP_USER/.ssh/authorized_keys"
  info "ключ деплоя добавлен"
fi
chown "$APP_USER:$APP_USER" "/home/$APP_USER/.ssh/authorized_keys"
chmod 600 "/home/$APP_USER/.ssh/authorized_keys"

# --- 2. Каталоги и .env ----------------------------------------------------------------------
install -d -m 750 -o "$APP_USER" -g "$APP_USER" "$APP_DIR"
install -d -m 750 -o "$APP_USER" -g "$APP_USER" "$APP_DIR/backups"

# Каталоги томов (infra/k8s/cluster/persistent-volumes.yaml). Владельца postgres и mongo их
# entrypoint ставит сам, kafka — init-контейнер. Файлы пишут core-api и media-worker под UID 10001.
install -d -m 711 -o root -g root "$DATA_DIR"
for d in postgres mongo kafka; do
  [[ -d "$DATA_DIR/$d" ]] || install -d -m 700 -o root -g root "$DATA_DIR/$d"
done
[[ -d "$DATA_DIR/files" ]] || install -d -m 750 -o 10001 -g 10001 "$DATA_DIR/files"

rnd() { openssl rand -base64 "$1" | tr -d '\n/+=' | cut -c1-"$2"; }

if [[ -f "$APP_DIR/.env" ]]; then
  info ".env уже есть, не трогаю (секреты менять нельзя: DATA_ENCRYPTION_KEY расшифровывает БД)"
else
  info "генерирую $APP_DIR/.env"
  umask 077
  # Без кавычек вокруг значений: kubectl --from-env-file их не снимает (infra/k8s/apply.sh).
  cat > "$APP_DIR/.env" <<ENVFILE
WEB_ORIGIN=http://${IP}
PUBLIC_BASE_URL=http://${IP}
COOKIE_SECURE=false

POSTGRES_DB=tutorcraft
POSTGRES_USER=tutorcraft
POSTGRES_PASSWORD=$(rnd 24 24)

MONGO_DB=tutorcraft
REDIS_PASSWORD=$(rnd 24 24)

STORAGE_DRIVER=local
S3_ENDPOINT=
S3_REGION=us-east-1
S3_BUCKET=
S3_ACCESS_KEY=
S3_SECRET_KEY=

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
  umask 022
fi

# --- 3. k3s ----------------------------------------------------------------------------------
# Сначала правило на порты, потом k3s: API не должен ни секунды слушать интернет открытым.
info "tutorcraft-k3s-guard (порты 6443/10250/10256 закрыты снаружи)"
install -m 755 "$INFRA/deploy/k3s/tutorcraft-k3s-guard.sh" /usr/local/sbin/tutorcraft-k3s-guard.sh
install -m 644 "$INFRA/deploy/k3s/tutorcraft-k3s-guard.service" /etc/systemd/system/tutorcraft-k3s-guard.service
systemctl daemon-reload
systemctl enable --now tutorcraft-k3s-guard.service
systemctl restart tutorcraft-k3s-guard.service

# 644: секретов в конфиге нет, а kubectl из k3s читает его при каждом запуске, в том числе под
# пользователем tutorcraft. С 600 каждая команда деплоя печатала бы «permission denied».
install -d -m 755 /etc/rancher/k3s
install -m 644 "$INFRA/deploy/k3s/config.yaml" /etc/rancher/k3s/config.yaml

if command -v k3s >/dev/null; then
  info "k3s уже установлен: $(k3s --version | head -1)"
else
  # Канал stable; конкретную версию можно закрепить: INSTALL_K3S_VERSION=v1.xx.y+k3s1 bash bootstrap-ip.sh …
  info "устанавливаю k3s (${INSTALL_K3S_VERSION:-канал stable})"
  curl -sfL https://get.k3s.io | INSTALL_K3S_CHANNEL="${INSTALL_K3S_CHANNEL:-stable}" sh -s -
fi

export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
for _ in $(seq 1 60); do k3s kubectl get nodes >/dev/null 2>&1 && break; sleep 3; done
k3s kubectl wait --for=condition=Ready node --all --timeout=180s

# --- 4. Namespace, тома, RBAC ----------------------------------------------------------------
info "namespace, PersistentVolume и RBAC деплоя"
k3s kubectl apply -f "$INFRA/k8s/cluster/namespace.yaml"
k3s kubectl apply -f "$INFRA/k8s/cluster/persistent-volumes.yaml" -f "$INFRA/k8s/cluster/deployer-rbac.yaml"

# --- 5. kubeconfig пользователя tutorcraft ---------------------------------------------------
token=""
for _ in $(seq 1 30); do
  token="$(k3s kubectl -n tutorcraft get secret deployer-token -o jsonpath='{.data.token}' 2>/dev/null | base64 -d || true)"
  [[ -n "$token" ]] && break
  sleep 2
done
[[ -n "$token" ]] || die "k3s не выпустил токен для ServiceAccount deployer"

KUBE_DIR="/home/$APP_USER/.kube"
install -d -m 700 -o "$APP_USER" -g "$APP_USER" "$KUBE_DIR"
# Через heredoc, а не `kubectl config set-credentials --token=…`: так токен не попадает в argv.
(
  umask 077
  cat > "$KUBE_DIR/config" <<EOF_KUBE
apiVersion: v1
kind: Config
clusters:
  - name: tutorcraft
    cluster:
      server: https://127.0.0.1:6443
      certificate-authority-data: $(base64 -w0 < /var/lib/rancher/k3s/server/tls/server-ca.crt)
users:
  - name: deployer
    user:
      token: ${token}
contexts:
  - name: tutorcraft
    context: { cluster: tutorcraft, user: deployer, namespace: tutorcraft }
current-context: tutorcraft
EOF_KUBE
)
unset token
chown "$APP_USER:$APP_USER" "$KUBE_DIR/config"
chmod 600 "$KUBE_DIR/config"

# kubectl из k3s без KUBECONFIG пытается читать /etc/rancher/k3s/k3s.yaml (только root).
profile="/home/$APP_USER/.profile"
grep -qs 'KUBECONFIG=' "$profile" || echo 'export KUBECONFIG="$HOME/.kube/config"' >> "$profile"
chown "$APP_USER:$APP_USER" "$profile"

sudo -u "$APP_USER" KUBECONFIG="$KUBE_DIR/config" kubectl auth can-i create deployments -n tutorcraft >/dev/null \
  || die "kubeconfig пользователя $APP_USER не даёт доступа к namespace tutorcraft"
info "kubeconfig $KUBE_DIR/config готов (только namespace tutorcraft)"

# --- 6. nginx --------------------------------------------------------------------------------
if ! command -v nginx >/dev/null; then
  info "nginx на хосте не найден, пропускаю установку vhost, поставьте его вручную"
else
  SNIPPETS=/etc/nginx/snippets
  install -d -m 755 "$SNIPPETS"
  cp "$INFRA/deploy/nginx/tutorcraft-proxy.conf" "$SNIPPETS/tutorcraft-proxy.conf"

  if [[ -d /etc/nginx/sites-available ]]; then
    VHOST=/etc/nginx/sites-available/tutorcraft.conf
  else
    VHOST=/etc/nginx/conf.d/tutorcraft.conf
  fi

  # Существующий vhost не перезаписываем: в нём может быть домен с TLS (так прод на
  # tutorcraft.sproogeekdev.tech однажды потерял HTTPS). Upstream в нём переводятся на k3s вручную,
  # см. docs/deployment-ip.md §3.2.
  installed=false
  if [[ -f "$VHOST" ]]; then
    info "vhost $VHOST уже есть, не трогаю. Upstream должны указывать на 10.43.0.100–102:"
    grep -nE '^\s*server [0-9.]+:[0-9]+;' "$VHOST" || true
  else
    cp "$INFRA/deploy/nginx/tutorcraft-ip.conf" "$VHOST"
    if [[ -d /etc/nginx/sites-enabled ]]; then ln -sfn "$VHOST" /etc/nginx/sites-enabled/tutorcraft.conf; fi
    installed=true
  fi

  # nginx -t обязателен: битый конфиг при reload уронит и confeek.
  if nginx -t; then
    systemctl reload nginx
    info "nginx перезагружен"
  else
    # Свой только что поставленный vhost убираем, чтобы следующий reload соседей не упал на нём.
    if $installed; then rm -f "$VHOST" /etc/nginx/sites-enabled/tutorcraft.conf; fi
    die "nginx -t не прошёл, reload не делал: confeek не затронут. Проверьте вывод выше"
  fi
fi

cat <<DONE

Готово. Дальше:
  1. Приватную часть ключа деплоя положить в секрет GitHub PROD_SSH_KEY.
  2. Пароль администратора:  sudo grep ADMIN_PASSWORD $APP_DIR/.env
     (на экран не печатаю, чтобы не осел в истории терминала и в логах CI)
  3. Скопировать $APP_DIR/.env в менеджер паролей: без него бэкап БД бесполезен.
  4. Запустить деплой: GitHub → Actions → Deploy to Production → Run workflow.

До первого деплоя http://${IP} отдаёт 502 — это нормально.
DONE
