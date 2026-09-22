#!/usr/bin/env bash
# Fixed finance release. Restore old application on failed verification; never restore live DB automatically.
set -Eeuo pipefail
umask 077
RELEASE=20260917-refund-finance
UPLOAD=/root/fitchain-release-20260917-refund-finance
BACKUP=/var/lib/fitchain/releases/20260917-refund-finance
FRONT=/var/www/fitchain-releases/20260917-refund-finance
MAINTENANCE_FLAG=/var/www/fitchain-refund-finance-maintenance.flag
PG=/usr/pgsql-18/bin
marker_owned=false
gate_owned_by_release=false
swapped=false
watchdog_was_active=false
app_recovery_required=false
old_front=

[[ $(id -u) == 0 ]]
[[ -f "$UPLOAD/STAGING_ACCEPTED" && -f "$UPLOAD/login-five-roles.py" && -f "$UPLOAD/refund-finance-acceptance.py" ]]
[[ -f "$UPLOAD/verify-finance-artifacts.py" && -f "$UPLOAD/finance-release-rollback.sh" && -f "$UPLOAD/fitchain-nginx.conf" ]]
python3 "$UPLOAD/verify-finance-artifacts.py" "$UPLOAD" --receipt "$UPLOAD/STAGING_ACCEPTED"
[[ ! -e "$BACKUP" && ! -e "$FRONT" && ! -e "$MAINTENANCE_FLAG" ]]
[[ ! -e /var/www/fitchain-finance-next-link && ! -e /var/www/fitchain-finance-rollback-link ]]
cmp "$UPLOAD/fitchain-nginx.conf" /etc/nginx/conf.d/fitchain.conf
nginx -t
[[ $(systemctl is-active postgresql-18) == active ]]
old_front=$(readlink -f /var/www/fitchain-current)
[[ "$old_front" == /var/www/fitchain-releases/* && -f "$old_front/index.html" ]]
[[ $(systemctl is-active fitchain-watchdog.timer || true) != active ]] || watchdog_was_active=true

restore_backend() { install -m 0640 -o fitchain -g fitchain "$BACKUP/fitness-backend.jar" /opt/fitchain/fitness-backend.jar; }
restore_frontend() {
  rm -f -- /var/www/fitchain-finance-rollback-link
  ln -s "$old_front" /var/www/fitchain-finance-rollback-link && mv -Tf /var/www/fitchain-finance-rollback-link /var/www/fitchain-current
}
restart_old_app() { systemctl restart fitchain; }
verify_old_app() {
  local healthy=false
  for i in $(seq 1 60); do
    if curl -fsS --max-time 2 http://127.0.0.1:9091/actuator/health >/dev/null 2>&1; then healthy=true; break; fi
    sleep 1
  done
  [[ "$healthy" == true ]]
}
restore_watchdog() {
  if [[ "$watchdog_was_active" == true ]]; then systemctl start fitchain-watchdog.timer; else return 0; fi
}
stop_application() { systemctl stop fitchain; }
verify_maintenance_gate() {
  [[ $(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 http://127.0.0.1/api/v1/auth/venues) == 503 ]]
}

# Cleanup is armed before watchdog, application, or maintenance state changes.
source "$UPLOAD/finance-release-rollback.sh"
trap 'finance_rollback $?' ERR
trap 'finance_rollback 130' INT
trap 'finance_rollback 143' TERM

systemctl stop fitchain-watchdog.timer
systemctl stop fitchain-watchdog.service 2>/dev/null || true
establish_initial_maintenance_gate
verify_maintenance_gate
stop_app_for_release

# Capture the dump and fingerprints only after this application is fully quiesced.
install -d -m 0700 "$BACKUP"
cp -p /opt/fitchain/fitness-backend.jar "$BACKUP/fitness-backend.jar"
printf '%s\n' "$old_front" > "$BACKUP/old-front-path"
systemctl start fitchain-backup.service
runuser -u postgres -- "$PG/pg_dump" -Fc fitness_db > "$BACKUP/fitness_db.dump"
"$PG/pg_restore" --list "$BACKUP/fitness_db.dump" >/dev/null
fingerprint="SELECT md5(string_agg(id::text||':'||username||':'||password_hash||':'||role_id::text||':'||COALESCE(venue_id::text,'null')||':'||status||':'||must_change_password::text,',' ORDER BY id)) FROM sys_user; SELECT 'member='||count(*) FROM member; SELECT 'orders='||count(*) FROM sales_order; SELECT 'entitlements='||count(*) FROM member_entitlement; SELECT 'bookings='||count(*) FROM booking; SELECT 'payments='||count(*) FROM payment_transaction; SELECT 'lessons='||count(*) FROM private_lesson;"
runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "$fingerprint" > "$BACKUP/before-fingerprint"
runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "SELECT version||':'||checksum FROM flyway_schema_history WHERE success AND version::int<=8 ORDER BY installed_rank" > "$BACKUP/before-migrations"

install -d -m 0755 "$FRONT"
tar -xzf "$UPLOAD/frontend-dist.tar.gz" -C "$FRONT"
[[ -f "$FRONT/index.html" ]]
find "$FRONT" -type d -exec chmod 0755 {} +
find "$FRONT" -type f -exec chmod 0644 {} +
swapped=true
install -m 0640 -o fitchain -g fitchain "$UPLOAD/fitness-backend.jar" /opt/fitchain/fitness-backend.jar
ln -s "$FRONT" /var/www/fitchain-finance-next-link
mv -Tf /var/www/fitchain-finance-next-link /var/www/fitchain-current
systemctl start fitchain
verify_old_app
[[ $(runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1") == 9 ]]
runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "$fingerprint" > "$BACKUP/after-fingerprint"
cmp "$BACKUP/before-fingerprint" "$BACKUP/after-fingerprint"
runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "SELECT version||':'||checksum FROM flyway_schema_history WHERE success AND version::int<=8 ORDER BY installed_rank" > "$BACKUP/after-migrations"
cmp "$BACKUP/before-migrations" "$BACKUP/after-migrations"
set -a
source /etc/fitchain/fitchain.env
set +a
# Loopback 8080 deliberately bypasses nginx while the public API remains quiesced.
python3 "$UPLOAD/refund-finance-acceptance.py" --db fitness_db --port 8080 --readonly
python3 "$UPLOAD/login-five-roles.py" --port 8080
curl -fsS --max-time 10 http://127.0.0.1:8080/api/v1/auth/venues >/dev/null

remove_owned_maintenance_gate
curl -fsS --max-time 10 http://127.0.0.1/login >/dev/null
curl -fsS --max-time 10 http://127.0.0.1/api/v1/auth/venues >/dev/null
restore_watchdog
trap - ERR INT TERM
gate_owned_by_release=false
printf '%s\n' "$RELEASE" > "$BACKUP/RELEASED"
echo "Finance release verified; backup retained at $BACKUP"
