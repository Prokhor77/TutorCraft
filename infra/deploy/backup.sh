#!/usr/bin/env bash
# Ежедневный бэкап TutorCraft: PostgreSQL (pg_dump) + MongoDB (mongodump), ротация по дням.
# Запуск из каталога с docker-compose.prod.yml (cron, см. docs/deployment.md):
#   0 3 * * * cd /opt/tutorcraft && ./infra/deploy/backup.sh >> backups/backup.log 2>&1
# Имена БД и пользователь берутся из окружения самих контейнеров — .env скрипт не читает.
# Файлы в S3 не копируются — за них отвечает провайдер (включите версионирование бакета).
set -euo pipefail

readonly COMPOSE_FILE="docker-compose.prod.yml"
readonly BACKUP_DIR="backups"
readonly RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "backup: $COMPOSE_FILE not found in $(pwd)" >&2
  exit 1
fi

compose() { docker compose -f "$COMPOSE_FILE" "$@"; }

stamp="$(date +%F_%H%M)"
mkdir -p "$BACKUP_DIR"

pg_file="$BACKUP_DIR/postgres_${stamp}.sql.gz"
compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$pg_file"

mongo_file="$BACKUP_DIR/mongo_${stamp}.archive.gz"
compose exec -T mongo mongodump --quiet --archive --gzip > "$mongo_file"

find "$BACKUP_DIR" -type f \( -name 'postgres_*.sql.gz' -o -name 'mongo_*.archive.gz' \) \
  -mtime "+${RETENTION_DAYS}" -delete

echo "backup ${stamp}: $(du -h "$pg_file" | cut -f1) postgres, $(du -h "$mongo_file" | cut -f1) mongo"
