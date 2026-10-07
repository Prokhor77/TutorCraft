#!/usr/bin/env bash
# Ежедневный бэкап TutorCraft: PostgreSQL (pg_dump) и MongoDB (mongodump) из подов k3s, ротация по дням.
# Запуск под пользователем tutorcraft (cron, см. docs/deployment-ip.md §9):
#   0 3 * * * cd /opt/tutorcraft && ./infra/deploy/backup.sh >> backups/backup.log 2>&1
# Имена БД и пользователь берутся из окружения самих подов, .env скрипт не читает.
#
# Загруженные файлы (STORAGE_DRIVER=local) — обычный каталог /opt/tutorcraft/data/files на хосте.
# Скрипт его не копирует: он большой, и место под копию на том же диске ничего не спасает.
# Снимайте его отдельно на другой сервер или в S3 (rsync/rclone от root).
set -euo pipefail

readonly NS="tutorcraft"
readonly BACKUP_DIR="backups"
readonly RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

# cron не читает ~/.profile, а kubectl из k3s без KUBECONFIG идёт в конфиг root.
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"

k() { kubectl -n "$NS" "$@"; }

stamp="$(date +%F_%H%M)"
mkdir -p "$BACKUP_DIR"

pg_file="$BACKUP_DIR/postgres_${stamp}.sql.gz"
k exec postgres-0 -c postgres -- sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$pg_file"

mongo_file="$BACKUP_DIR/mongo_${stamp}.archive.gz"
k exec mongo-0 -c mongo -- mongodump --quiet --archive --gzip > "$mongo_file"

find "$BACKUP_DIR" -type f \( -name 'postgres_*.sql.gz' -o -name 'mongo_*.archive.gz' \) \
  -mtime "+${RETENTION_DAYS}" -delete

echo "backup ${stamp}: $(du -h "$pg_file" | cut -f1) postgres, $(du -h "$mongo_file" | cut -f1) mongo"
