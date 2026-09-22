#!/usr/bin/env bash
set -Eeuo pipefail

for username in manager coach reception member; do
  payload=$(printf '{"username":"%s","password":"password"}' "$username")
  response=$(curl --silent --show-error --max-time 10 \
    --header 'Content-Type: application/json' \
    --data "$payload" \
    http://127.0.0.1/api/v1/auth/login)
  USERNAME="$username" LOGIN_RESPONSE="$response" python3 -c 'import json, os; body=json.loads(os.environ["LOGIN_RESPONSE"]); data=body.get("data") or {}; info=data.get("userInfo") or {}; print(os.environ["USERNAME"] + "|" + ("OK" if data.get("accessToken") else "FAIL") + "|" + str(info.get("role", "")))'
done

runuser -u postgres -- /usr/pgsql-18/bin/psql -d fitness_db -Atc \
  "SELECT u.username||'|'||r.code||'|'||u.status FROM sys_user u JOIN role r ON r.id=u.role_id ORDER BY u.id;"
