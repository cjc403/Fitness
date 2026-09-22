#!/usr/bin/env bash
set -Eeuo pipefail

upload_dir=${1:-/root/fitchain-upload}
env_dir=/etc/fitchain
env_file=$env_dir/fitchain.env
release_root=/var/www/fitchain-releases

if [[ ! -f "$upload_dir/fitness-backend-1.0.0.jar" || ! -f "$upload_dir/frontend-dist.tar.gz" ]]; then
  echo "Deployment artifacts are incomplete in $upload_dir" >&2
  exit 1
fi

IFS= read -r admin_password_b64
if [[ -z "$admin_password_b64" ]]; then
  echo "The administrator password was not supplied" >&2
  exit 1
fi
admin_password=$(printf '%s' "$admin_password_b64" | tr -d '\r\n' | base64 --decode)

install -d -m 0750 "$env_dir"
if [[ ! -f "$env_file" ]]; then
  db_password=$(openssl rand -hex 24)
  jwt_secret=$(openssl rand -hex 48)
  umask 077
  {
    printf "DB_URL='jdbc:postgresql://127.0.0.1:5432/fitness_db?sslmode=disable&connectTimeout=5&socketTimeout=30'\n"
    printf 'DB_USERNAME=fitness_user\n'
    printf 'DB_PASSWORD=%s\n' "$db_password"
    printf 'JWT_SECRET=%s\n' "$jwt_secret"
    printf "APP_ADMIN_PASSWORD='%s'\n" "$admin_password"
    printf 'SPRING_PROFILES_ACTIVE=prod\n'
    printf 'SERVER_ADDRESS=127.0.0.1\n'
    printf 'MANAGEMENT_ADDRESS=127.0.0.1\n'
    printf 'MANAGEMENT_PORT=9091\n'
    printf 'DB_POOL_MAX=8\n'
    printf 'DB_POOL_MIN=2\n'
    printf 'LOG_FILE=/var/log/fitchain/fitness-backend.log\n'
  } > "$env_file"
  chmod 0600 "$env_file"
fi

set -a
# shellcheck disable=SC1090
source "$env_file"
set +a

if [[ ! -s /var/lib/pgsql/18/data/PG_VERSION ]]; then
  /usr/pgsql-18/bin/postgresql-18-setup initdb
fi
systemctl enable --now postgresql-18.service

if ! runuser -u postgres -- /usr/pgsql-18/bin/psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='fitness_user'" | grep -q 1; then
  printf "CREATE ROLE fitness_user LOGIN PASSWORD '%s';\n" "$DB_PASSWORD" | runuser -u postgres -- /usr/pgsql-18/bin/psql --set ON_ERROR_STOP=1
else
  printf "ALTER ROLE fitness_user WITH LOGIN PASSWORD '%s';\n" "$DB_PASSWORD" | runuser -u postgres -- /usr/pgsql-18/bin/psql --set ON_ERROR_STOP=1
fi

if ! runuser -u postgres -- /usr/pgsql-18/bin/psql -tAc "SELECT 1 FROM pg_database WHERE datname='fitness_db'" | grep -q 1; then
  runuser -u postgres -- /usr/pgsql-18/bin/createdb --owner=fitness_user fitness_db
fi

dump_file=$(find "$upload_dir" -maxdepth 1 -type f -name 'fitness_db-*.dump' -printf '%T@ %p\n' | sort -nr | head -n 1 | cut -d' ' -f2-)
if [[ -z "$dump_file" ]]; then
  echo "No PostgreSQL backup was uploaded" >&2
  exit 1
fi

table_count=$(PGPASSWORD="$DB_PASSWORD" /usr/pgsql-18/bin/psql --host=127.0.0.1 --username=fitness_user --dbname=fitness_db --tuples-only --no-align --command="SELECT count(*) FROM pg_tables WHERE schemaname='public';")
if [[ "$table_count" == "0" ]]; then
  PGPASSWORD="$DB_PASSWORD" /usr/pgsql-18/bin/pg_restore --host=127.0.0.1 --username=fitness_user --dbname=fitness_db --no-owner --exit-on-error "$dump_file"
fi

id fitchain >/dev/null 2>&1 || useradd --system --home-dir /opt/fitchain --shell /sbin/nologin fitchain
install -d -m 0750 -o fitchain -g fitchain /opt/fitchain /var/log/fitchain
install -m 0640 -o fitchain -g fitchain "$upload_dir/fitness-backend-1.0.0.jar" /opt/fitchain/fitness-backend.jar

release_dir="$release_root/$(date +%Y%m%d-%H%M%S)"
install -d -m 0755 "$release_dir"
tar -xzf "$upload_dir/frontend-dist.tar.gz" -C "$release_dir"
ln -sfn "$release_dir" /var/www/fitchain-current

install -m 0644 "$upload_dir/fitchain.service" /etc/systemd/system/fitchain.service
install -m 0644 "$upload_dir/fitchain-backup.service" /etc/systemd/system/fitchain-backup.service
install -m 0644 "$upload_dir/fitchain-backup.timer" /etc/systemd/system/fitchain-backup.timer
install -m 0644 "$upload_dir/fitchain-watchdog.service" /etc/systemd/system/fitchain-watchdog.service
install -m 0644 "$upload_dir/fitchain-watchdog.timer" /etc/systemd/system/fitchain-watchdog.timer
install -m 0644 "$upload_dir/fitchain-nginx.conf" /etc/nginx/conf.d/fitchain.conf
install -m 0750 "$upload_dir/fitchain-backup.sh" /usr/local/sbin/fitchain-backup
install -m 0755 "$upload_dir/fitchain-healthcheck.sh" /usr/local/sbin/fitchain-healthcheck
install -m 0750 "$upload_dir/fitchain-watchdog.sh" /usr/local/sbin/fitchain-watchdog

nginx -t
systemctl daemon-reload
systemctl enable --now nginx.service fitchain.service fitchain-backup.timer fitchain-watchdog.timer

for _ in {1..90}; do
  if /usr/local/sbin/fitchain-healthcheck >/dev/null 2>&1; then
    /usr/local/sbin/fitchain-healthcheck
    exit 0
  fi
  sleep 1
done

systemctl status --no-pager fitchain.service nginx.service postgresql-18.service || true
journalctl --no-pager -u fitchain.service -n 100 || true
exit 1
