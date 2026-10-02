#!/usr/bin/env bash
set -euo pipefail

# Load local environment vars from .env if present.
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

: "${DB_NAME:?Set DB_NAME before running the backup}"
: "${DB_USER:?Set DB_USER before running the backup}"
: "${DB_PASSWORD:?Set DB_PASSWORD before running the backup}"

backup_dir="${BACKUP_DIR:-./backups}"
db_host="${DB_HOST:-127.0.0.1}"
db_port="${DB_PORT:-5432}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${backup_dir%/}/taskmanager-${timestamp}.dump"

mkdir -p "$backup_dir"
umask 077
PGPASSWORD="$DB_PASSWORD" pg_dump \
  --host="$db_host" \
  --port="$db_port" \
  --username="$DB_USER" \
  --dbname="$DB_NAME" \
  --no-password \
  --format=custom \
  --file="$backup_file"
unset PGPASSWORD

printf 'Backup created: %s\n' "$backup_file"
