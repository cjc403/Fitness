#!/usr/bin/env bash
# Fixed, single-use release. Existing database is never restored automatically.
set -Eeuo pipefail
RELEASE=20261017-mobile-agent-v23
UPLOAD=/root/fitchain-release-$RELEASE
BACKUP=/var/lib/fitchain/releases/$RELEASE
FRONT=/var/www/fitchain-releases/$RELEASE
CURRENT=/var/www/fitchain-current
LIVE_JAR=/opt/fitchain/fitness-backend.jar
ENV_FILE=/etc/fitchain/fitchain.env
NGINX_FILE=/etc/nginx/conf.d/fitchain.conf
MAINTENANCE_FLAG=/var/www/fitchain-refund-finance-maintenance.flag
PG=/usr/pgsql-18/bin
marker_owned=false; gate_owned_by_release=false; swapped=false; app_recovery_required=false
source "$(dirname "${BASH_SOURCE[0]}")/finance-release-rollback.sh"

place_file(){ install -m "$3" -o "$4" -g "$4" "$1" "$2"; }
switch_frontend(){
  local temporary
  temporary=$(mktemp -d "$CURRENT.link-$RELEASE.XXXXXX") || return 1
  if ! ln -s "$1" "$temporary/current"; then rmdir "$temporary"; return 1; fi
  if ! mv -Tf "$temporary/current" "$CURRENT"; then rm -f -- "$temporary/current"; rmdir "$temporary"; return 1; fi
  rmdir "$temporary"
}
stop_application(){ systemctl stop fitchain; }
restore_watchdog(){ if [[ $watchdog_was_active == true ]]; then systemctl start fitchain-watchdog.timer; fi; }
restart_old_app(){
  local failed=0
  nginx -t || failed=1
  systemctl reload nginx || failed=1
  systemctl restart fitchain || failed=1
  return "$failed"
}
restore_backend(){
  local failed=0
  systemctl stop fitchain || failed=1
  place_file "$BACKUP/fitness-backend.jar" "$LIVE_JAR" 0640 fitchain || failed=1
  place_file "$BACKUP/fitchain.env" "$ENV_FILE" 0600 root || failed=1
  place_file "$BACKUP/fitchain.conf" "$NGINX_FILE" 0644 root || failed=1
  return "$failed"
}
restore_frontend(){ switch_frontend "$OLD_FRONT"; }
healthy(){
  local i
  for i in $(seq 1 60); do
    if curl -fsS --max-time 2 http://127.0.0.1:9091/actuator/health/readiness | grep -q '"status":"UP"'; then return 0; fi
    sleep 2
  done
  return 1
}
verify_old_app(){ healthy || return 1; [[ $(sha256sum "$LIVE_JAR" | cut -d' ' -f1) == "$OLD_SHA" ]]; }
verify_maintenance_gate(){ [[ $(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1/api/v1/auth/me) == 503 ]]; }
snapshot_database(){ python3 "$UPLOAD/snapshot.py" fitness_db >"$1"; }
create_database_backup(){
  runuser -u postgres -- "$PG/pg_dump" -Fc fitness_db >"$BACKUP/database.dump"
  [[ -s "$BACKUP/database.dump" ]]
  "$PG/pg_restore" --list "$BACKUP/database.dump" >"$BACKUP/database.list"
  sha256sum "$BACKUP/database.dump" >"$BACKUP/database.dump.sha256"
  sha256sum -c "$BACKUP/database.dump.sha256"
}
backup_state(){
  cp -p "$LIVE_JAR" "$BACKUP/fitness-backend.jar"
  cp -p "$ENV_FILE" "$BACKUP/fitchain.env"
  cp -p "$NGINX_FILE" "$BACKUP/fitchain.conf"
  printf '%s\n' "$OLD_FRONT" >"$BACKUP/old-frontend.txt"
  create_database_backup
  snapshot_database "$BACKUP/data-before.json"
}
verify_upload(){
  [[ $(id -u) == 0 && ! -e "$BACKUP" && ! -e "$FRONT" && ! -e "$MAINTENANCE_FLAG" && -L "$CURRENT" ]]
  [[ -f "$UPLOAD/SHA256SUMS" && -f "$UPLOAD/stage-passed.json" && -f "$UPLOAD/candidate.env" ]]
  (cd "$UPLOAD" && sha256sum -c SHA256SUMS)
  python3 "$UPLOAD/verify-candidate.py" "$UPLOAD"
  OLD_FRONT=$(readlink -f "$CURRENT"); OLD_SHA=$(sha256sum "$LIVE_JAR" | cut -d' ' -f1)
  [[ -d "$OLD_FRONT" ]]
  install -d -m 0700 "$BACKUP"
}
install_candidate(){
  install -d -m 0755 "$FRONT"
  tar -xzf "$UPLOAD/frontend-dist.tar.gz" -C "$FRONT"
  [[ -f "$FRONT/index.html" && -f "$FRONT/manifest.webmanifest" && -f "$FRONT/sw.js" ]]
  place_file "$UPLOAD/fitness-backend.jar" "$LIVE_JAR" 0640 fitchain
  place_file "$UPLOAD/candidate.env" "$ENV_FILE" 0600 root
  place_file "$UPLOAD/fitchain-nginx.conf" "$NGINX_FILE" 0644 root
  nginx -t
  switch_frontend "$FRONT"
  systemctl reload nginx
}
verify_candidate(){
  healthy
  [[ $(runuser -u postgres -- "$PG/psql" -X -qAt -d fitness_db -c 'select max(version::int) from flyway_schema_history where success') == 23 ]]
  [[ $(sha256sum "$LIVE_JAR" | cut -d' ' -f1) == $(sha256sum "$UPLOAD/fitness-backend.jar" | cut -d' ' -f1) ]]
  python3 "$UPLOAD/verify-candidate.py" "$UPLOAD" "$FRONT"
  snapshot_database "$BACKUP/data-after.json"
  python3 "$UPLOAD/compare-snapshots.py" "$BACKUP/data-before.json" "$BACKUP/data-after.json"
  [[ $(curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://127.0.0.1/login) == 200 ]]
}
perform_release(){
  verify_upload
  watchdog_was_active=false
  if systemctl is-active --quiet fitchain-watchdog.timer; then watchdog_was_active=true; fi
  trap 'finance_rollback $?' ERR
  trap 'finance_rollback 130' INT
  trap 'finance_rollback 143' TERM
  systemctl stop fitchain-watchdog.timer
  systemctl stop fitchain-watchdog.service
  establish_initial_maintenance_gate
  verify_maintenance_gate
  stop_app_for_release
  backup_state
  swapped=true
  install_candidate
  systemctl start fitchain
  verify_candidate
  restore_watchdog
  remove_owned_maintenance_gate
  trap - ERR INT TERM
  printf 'RELEASE_SUCCEEDED %s\n' "$RELEASE"
}
if [[ ${FITCHAIN_RELEASE_LIBRARY_ONLY:-} != 1 ]]; then perform_release; fi
