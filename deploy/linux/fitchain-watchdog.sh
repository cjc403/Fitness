#!/usr/bin/env bash
set -Eeuo pipefail

if /usr/local/sbin/fitchain-healthcheck >/dev/null 2>&1; then
  exit 0
fi

echo "FitChain health check failed; restarting managed services" >&2
systemctl restart postgresql-18.service
systemctl restart fitchain.service nginx.service

for _ in {1..90}; do
  if /usr/local/sbin/fitchain-healthcheck >/dev/null 2>&1; then
    echo "FitChain recovered successfully"
    exit 0
  fi
  sleep 1
done

echo "FitChain did not recover after automatic restart" >&2
exit 1
