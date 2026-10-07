#!/usr/bin/env bash
# Применяет отрендеренные манифесты на сервере. deploy-production.yml вызывает его по SSH под
# пользователем tutorcraft. kubeconfig — ServiceAccount deployer с правами только на namespace
# tutorcraft.
#
#   cd /opt/tutorcraft && GHCR_PULL_USER=… GHCR_PULL_TOKEN=… infra/k8s/apply.sh
#
# Порядок вместо depends_on из compose:
#   1. секреты из .env и pull-токена;
#   2. хранилища (tier=data), ожидание готовности, Job с топиками Kafka;
#   3. приложения (tier=app), ожидание раскатки.
# Без GHCR_PULL_TOKEN секрет ghcr не трогается: так можно перезапустить apply.sh вручную.
set -euo pipefail

readonly NS=tutorcraft
readonly MANIFEST=infra/k8s/tutorcraft.rendered.yaml

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# Через ssh-action ~/.profile не читается, а kubectl из k3s без KUBECONFIG идёт в
# /etc/rancher/k3s/k3s.yaml, доступный только root.
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"

die() { echo "ОШИБКА: $*" >&2; exit 1; }
info() { echo "==> $*"; }
k() { kubectl -n "$NS" "$@"; }

command -v kubectl >/dev/null || die "kubectl не найден: k3s не установлен (docs/deployment-ip.md §3)"
[[ -f .env ]] || die ".env отсутствует в $(pwd): запустите infra/deploy/bootstrap-ip.sh"
[[ -f $MANIFEST ]] || die "$MANIFEST не найден: он приезжает из deploy-production.yml"
k auth can-i create deployments >/dev/null 2>&1 || die "нет доступа к namespace $NS: проверьте ~/.kube/config (bootstrap-ip.sh)"

# Диагностика при провале: события и хвост логов, как `docker logs --tail` раньше.
explain() {
  local target=$1
  echo "--- $target: describe ---"
  k describe "$target" | tail -n 40 || true
  echo "--- $target: logs ---"
  k logs "$target" --all-containers --tail=100 || true
}

wait_rollout() {
  local timeout=$1; shift
  local target
  for target in "$@"; do
    if ! k rollout status "$target" --timeout="$timeout"; then
      explain "$target"
      die "$target не поднялся за $timeout"
    fi
  done
}

# --- 1. Секреты ------------------------------------------------------------------------------
# Значения в .env записываются без кавычек: --from-env-file, в отличие от compose, их не снимает.
info "secret/tutorcraft-env из .env"
k create secret generic tutorcraft-env --from-env-file=.env --dry-run=client -o yaml | k apply -f -

if [[ -n "${GHCR_PULL_TOKEN:-}" ]]; then
  info "secret/ghcr (pull из GHCR)"
  # JSON собирается встроенным printf, а не через --docker-password: так токен не попадает в
  # аргументы процесса, которые на общем сервере видны всем через ps.
  auth="$(printf '%s:%s' "${GHCR_PULL_USER:?GHCR_PULL_USER не задан}" "$GHCR_PULL_TOKEN" | base64 | tr -d '\n')"
  printf '{"auths":{"ghcr.io":{"auth":"%s"}}}' "$auth" \
    | k create secret generic ghcr --type=kubernetes.io/dockerconfigjson \
        --from-file=.dockerconfigjson=/dev/stdin --dry-run=client -o yaml \
    | k apply -f -
  unset auth
fi

# Хэш .env попадает в аннотацию подов: правка .env перезапускает их и без смены образа.
env_sha="$(sha256sum .env | cut -c1-16)"
rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT
sed "s/__ENV_SHA__/${env_sha}/g" "$MANIFEST" > "$rendered"

# Job неизменяем: без удаления apply упадёт на «field is immutable», если изменился шаблон.
k delete job kafka-init --ignore-not-found --wait=true

# Проверка сервером до каких-либо изменений: ошибка в схеме не должна оставить стек наполовину обновлённым.
info "проверка манифестов (dry-run=server)"
kubectl apply --dry-run=server -f "$rendered" >/dev/null

# --- 2. Хранилища и топики -------------------------------------------------------------------
info "хранилища"
kubectl apply -f "$rendered" -l tutorcraft/tier=data
wait_rollout 300s statefulset/postgres statefulset/mongo statefulset/kafka deployment/redis

info "топики Kafka"
if ! k wait --for=condition=complete job/kafka-init --timeout=300s; then
  explain job/kafka-init
  die "kafka-init не завершился"
fi

# --- 3. Приложения ---------------------------------------------------------------------------
info "приложения"
kubectl apply -f "$rendered" -l tutorcraft/tier=app
# core-api на холодном старте: Flyway + прогрев, до ~2 минут, плюс pull образа.
wait_rollout 600s deployment/core-api
wait_rollout 300s deployment/web deployment/notifier deployment/media-worker

k get pods -o wide
info "готово"
