#!/usr/bin/env bash
# Создаёт .env из .env.example и генерирует случайные секреты для локальной разработки.
# Существующий .env не перезаписывается. Секреты не выводятся в консоль.
set -euo pipefail
cd "$(dirname "$0")/.."
ENV_FILE=".env"
if [[ -f "$ENV_FILE" ]]; then
  echo ".env already exists — leaving it untouched"
  exit 0
fi
cp .env.example "$ENV_FILE"
random() { openssl rand -base64 "$1" | tr -d '\n/+=' | cut -c1-"$2"; }
set_value() {
  local key="$1" value="$2"
  python3 - "$ENV_FILE" "$key" "$value" <<'PY'
import sys, re
path, key, value = sys.argv[1:]
text = open(path).read()
text = re.sub(rf"^{key}=.*$", f"{key}={value}", text, flags=re.M)
open(path, "w").write(text)
PY
}
set_value POSTGRES_PASSWORD "$(random 24 24)"
set_value REDIS_PASSWORD "$(random 24 24)"
set_value S3_ACCESS_KEY "tc$(random 12 14)"
set_value S3_SECRET_KEY "$(random 32 32)"
set_value JWT_SECRET "$(random 48 64)"
set_value DATA_ENCRYPTION_KEY "$(random 32 44)"
set_value SEED_DEMO_PASSWORD "Demo-$(random 12 12)1"
chmod 600 "$ENV_FILE"
echo ".env created with generated local secrets (see SEED_DEMO_PASSWORD inside for demo logins)"
