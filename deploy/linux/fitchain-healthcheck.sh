#!/usr/bin/env bash
set -Eeuo pipefail

backend_status=$(curl --silent --show-error --fail --max-time 5 http://127.0.0.1:9091/actuator/health)
frontend_code=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 http://127.0.0.1/login)

if [[ "$backend_status" != *'"status":"UP"'* ]]; then
  echo "Backend health check failed: $backend_status" >&2
  exit 1
fi

if [[ "$frontend_code" != "200" ]]; then
  echo "Frontend health check failed with HTTP $frontend_code" >&2
  exit 1
fi

echo "FitChain is healthy"
