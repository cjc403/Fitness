#!/usr/bin/env bash
set -Eeuo pipefail

set -a
# shellcheck disable=SC1091
source /etc/fitchain/fitchain.env
set +a

payload=$(python3 -c 'import json, os; print(json.dumps({"username": "admin", "password": os.environ["APP_ADMIN_PASSWORD"]}))')
response=$(curl --silent --show-error --fail --max-time 10 \
  --header 'Content-Type: application/json' \
  --data "$payload" \
  http://127.0.0.1/api/v1/auth/login)

LOGIN_RESPONSE="$response" python3 -c 'import json, os; body=json.loads(os.environ["LOGIN_RESPONSE"]); data=body.get("data") or {}; user=data.get("userInfo") or {}; assert data.get("accessToken"), "access token missing"; assert user.get("role") == "SUPER_ADMIN", "unexpected role"; print("Admin login OK: " + user.get("username", "unknown") + " / " + user.get("role", "unknown"))'

curl --silent --show-error --fail --max-time 10 http://127.0.0.1/api/v1/auth/venues >/dev/null
/usr/local/sbin/fitchain-healthcheck
echo "FitChain smoke test passed"
