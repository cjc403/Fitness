#!/usr/bin/env bash
# Only bind loopback. Requires the uploaded candidate; never touches production service/env.
set -Eeuo pipefail
umask 077
ROOT=/opt/fitchain-stage-finance-20260917
UPLOAD=/root/fitchain-release-20260917-refund-finance
UNIT=fitchain-stage-finance-20260917
[[ $(id -u) == 0 && -d "$ROOT" && -f "$UPLOAD/fitness-backend.jar" ]]
[[ -f "$UPLOAD/verify-finance-artifacts.py" ]]
python3 "$UPLOAD/verify-finance-artifacts.py" "$UPLOAD"
[[ $(systemctl is-active "$UNIT" || true) != active ]]
[[ -z $(ss -H -ltn 'sport = :18080' || true) && -z $(ss -H -ltn 'sport = :19091' || true) ]]
install -m 0640 -o fitchain -g fitchain "$UPLOAD/fitness-backend.jar" "$ROOT/fitness-backend.jar"
systemctl reset-failed "$UNIT" 2>/dev/null || true
systemd-run --unit="$UNIT" --property=User=fitchain --property=Group=fitchain \
  --property=WorkingDirectory="$ROOT" --property=EnvironmentFile=/etc/fitchain/fitchain.env \
  /usr/bin/java -Xms128m -Xmx512m -XX:MaxMetaspaceSize=192m -jar "$ROOT/fitness-backend.jar" \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/fitchain_stage_finance_20260917 \
  --spring.datasource.hikari.maximum-pool-size=5 --spring.datasource.hikari.minimum-idle=1 \
  --server.address=127.0.0.1 --server.port=18080 --management.server.port=19091 \
  --app.ai.enabled=false --logging.file.name="$ROOT/stage.log"
for i in $(seq 1 60); do
  if curl -fsS --max-time 2 http://127.0.0.1:19091/actuator/health >/dev/null 2>&1; then
    echo 'Isolated finance staging healthy'; exit 0
  fi
  sleep 1
done
echo 'Staging failed health check; production not changed' >&2
exit 1
