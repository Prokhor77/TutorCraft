#!/usr/bin/env bash
# Рендерит прод-манифесты в один YAML на stdout. Запускается в CI (deploy-production.yml, ci.yml)
# и локально для проверки:
#   infra/k8s/render.sh sha-abc1234 > /tmp/tutorcraft.yaml
# Без аргумента остаётся тег latest. Нужен только kubectl: kustomize в него встроен.
#
# Тег подставляется заменой строки, а не через `kustomize edit set image`: тогда не нужен отдельный
# бинарник kustomize, а в выводе сразу виден итоговый образ.
set -euo pipefail

tag="${1:-latest}"
[[ $tag =~ ^[A-Za-z0-9_][A-Za-z0-9._-]{0,127}$ ]] || { echo "render: недопустимый тег образа: $tag" >&2; exit 1; }

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# LoadRestrictionsNone: create-topics.sh лежит в infra/kafka/, вне каталога kustomization.
kubectl kustomize --load-restrictor=LoadRestrictionsNone "$here/app" \
  | sed -E "s#(ghcr\.io/prokhor77/tutorcraft-[a-z-]+):latest#\1:${tag}#g"
