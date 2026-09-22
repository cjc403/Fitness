#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
[[ $(id -u) == 0 ]]
PG=/usr/pgsql-18/bin
STAGE_DB=fitchain_stage_pt_20260917
TEST_DB=fitchain_test_pt_20260917
STAGE_ROOT=/opt/fitchain-stage-pt-20260917
[[ ! -e /root/fitchain-pt-test-password ]]
[[ -z $(runuser -u postgres -- "$PG/psql" -X -Atc "SELECT datname FROM pg_database WHERE datname IN ('$STAGE_DB','$TEST_DB')") ]]
[[ -z $(runuser -u postgres -- "$PG/psql" -X -Atc "SELECT rolname FROM pg_roles WHERE rolname='fitchain_pt_test'") ]]
set -a
source /etc/fitchain/fitchain.env
set +a
install -d -m 0750 -o fitchain -g fitchain "$STAGE_ROOT"
systemctl start fitchain-backup.service
runuser -u postgres -- "$PG/pg_dump" -Fc fitness_db > "$STAGE_ROOT/before.dump"
"$PG/pg_restore" --list "$STAGE_ROOT/before.dump" >/dev/null
runuser -u postgres -- "$PG/createdb" -O "$DB_USERNAME" "$STAGE_DB"
runuser -u postgres -- "$PG/pg_restore" --no-owner --role="$DB_USERNAME" -d "$STAGE_DB" < "$STAGE_ROOT/before.dump"
openssl rand -hex 24 > /root/fitchain-pt-test-password
test_password=$(</root/fitchain-pt-test-password)
runuser -u postgres -- "$PG/psql" -X -v ON_ERROR_STOP=1 -c "CREATE ROLE fitchain_pt_test LOGIN PASSWORD '$test_password'"
runuser -u postgres -- "$PG/createdb" -O fitchain_pt_test "$TEST_DB"
echo 'Isolated cloned migration database and disposable integration-test database prepared; production unchanged'
