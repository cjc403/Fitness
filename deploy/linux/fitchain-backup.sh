#!/usr/bin/env bash
set -Eeuo pipefail

umask 077
backup_dir=/var/lib/fitchain/backups
env_file=/etc/fitchain/fitchain.env
mkdir -p "$backup_dir"

if [[ ! -r "$env_file" ]]; then
  echo "FitChain environment file is unavailable" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

stamp=$(date +%Y%m%d-%H%M%S)
backup_file="$backup_dir/fitness_db-$stamp.dump"

PGPASSWORD="$DB_PASSWORD" /usr/pgsql-18/bin/pg_dump \
  --host=127.0.0.1 \
  --port=5432 \
  --username="$DB_USERNAME" \
  --dbname=fitness_db \
  --format=custom \
  --compress=9 \
  --no-owner \
  --file="$backup_file"

sha256sum "$backup_file" > "$backup_file.sha256"
find "$backup_dir" -type f \( -name 'fitness_db-*.dump' -o -name 'fitness_db-*.dump.sha256' \) -mtime +13 -delete
echo "Backup completed: $backup_file"
