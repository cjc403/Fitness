#!/usr/bin/env bash
# Fixed, scoped release. Does not reinstall services, rotate credentials, or restore a live database automatically.
set -Eeuo pipefail
umask 077
RELEASE=20260917-private-training
UPLOAD=/root/fitchain-release-20260917-private-training
BACKUP=/var/lib/fitchain/releases/20260917-private-training
FRONT=/var/www/fitchain-releases/20260917-private-training
PG=/usr/pgsql-18/bin
[[ $(id -u) == 0 ]]
[[ -f "$UPLOAD/fitness-backend.jar" && -f "$UPLOAD/frontend-dist.tar.gz" && -f "$UPLOAD/SHA256SUMS" ]]
[[ -f "$UPLOAD/STAGING_ACCEPTED" ]]
cmp "$UPLOAD/SHA256SUMS" "$UPLOAD/STAGING_ACCEPTED"
[[ ! -e "$BACKUP" && ! -e "$FRONT" ]]
(cd "$UPLOAD"; sha256sum -c SHA256SUMS)
nginx -t
[[ $(systemctl is-active postgresql-18) == active ]]
old_front=$(readlink -f /var/www/fitchain-current)
[[ "$old_front" == /var/www/fitchain-releases/* && -f "$old_front/index.html" ]]
install -d -m 0700 "$BACKUP"
cp -p /opt/fitchain/fitness-backend.jar "$BACKUP/fitness-backend.jar"
printf '%s\n' "$old_front" > "$BACKUP/old-front-path"
systemctl start fitchain-backup.service
runuser -u postgres -- "$PG/pg_dump" -Fc fitness_db > "$BACKUP/fitness_db.dump"
"$PG/pg_restore" --list "$BACKUP/fitness_db.dump" >/dev/null
fingerprint="SELECT md5(string_agg(id::text||':'||username||':'||password_hash||':'||role_id::text||':'||venue_id::text||':'||status,',' ORDER BY id)) FROM sys_user; SELECT 'member='||count(*) FROM member; SELECT 'orders='||count(*) FROM sales_order; SELECT 'entitlements='||count(*) FROM member_entitlement; SELECT 'bookings='||count(*) FROM booking;"
runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "$fingerprint" > "$BACKUP/before-fingerprint"
install -d -m 0755 "$FRONT"
tar -xzf "$UPLOAD/frontend-dist.tar.gz" -C "$FRONT"
[[ -f "$FRONT/index.html" ]]
find "$FRONT" -type d -exec chmod 0755 {} +
find "$FRONT" -type f -exec chmod 0644 {} +
swapped=false
watchdog_was_active=false
[[ $(systemctl is-active fitchain-watchdog.timer) != active ]] || watchdog_was_active=true
systemctl stop fitchain-watchdog.timer
rollback() {
  result=$?
  trap - ERR
  if [[ "$swapped" == true ]]; then
    echo 'Verification failed; restoring previous backend and frontend; retaining additive schema and current business data' >&2
    install -m 0640 -o fitchain -g fitchain "$BACKUP/fitness-backend.jar" /opt/fitchain/fitness-backend.jar
    ln -s "$old_front" /var/www/fitchain-rollback-link
    mv -Tf /var/www/fitchain-rollback-link /var/www/fitchain-current
    systemctl restart fitchain
  fi
  [[ "$watchdog_was_active" != true ]] || systemctl start fitchain-watchdog.timer
  exit "$result"
}
trap rollback ERR
swapped=true
systemctl stop fitchain
install -m 0640 -o fitchain -g fitchain "$UPLOAD/fitness-backend.jar" /opt/fitchain/fitness-backend.jar
ln -s "$FRONT" /var/www/fitchain-next-link
mv -Tf /var/www/fitchain-next-link /var/www/fitchain-current
systemctl start fitchain
healthy=false
for i in $(seq 1 60); do
  if /usr/local/sbin/fitchain-healthcheck >/dev/null 2>&1; then healthy=true;break;fi
  sleep 1
done
[[ "$healthy" == true ]]
[[ $(runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1") == 8 ]]
runuser -u postgres -- "$PG/psql" -X -d fitness_db -Atc "$fingerprint" > "$BACKUP/after-fingerprint"
cmp "$BACKUP/before-fingerprint" "$BACKUP/after-fingerprint"
set -a
source /etc/fitchain/fitchain.env
set +a
python3 "$UPLOAD/private-training-acceptance.py" --db fitness_db --port 8080 --readonly
curl -fsS --max-time 10 http://127.0.0.1/login >/dev/null
[[ "$watchdog_was_active" != true ]] || systemctl start fitchain-watchdog.timer
trap - ERR
printf '%s\n' "$RELEASE" > "$BACKUP/RELEASED"
echo "Release verified; backup: $BACKUP; previous frontend: $old_front"
