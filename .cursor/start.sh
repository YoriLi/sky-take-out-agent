#!/usr/bin/env bash
# Per-boot service reconciliation: bring up MySQL and Redis, then return.
# The backend and frontend run as long-lived processes in `terminals`.
set -euo pipefail

echo "==> Starting MySQL"
sudo service mysql start || true
echo "==> Starting Redis"
sudo service redis-server start || true

echo "==> Waiting for MySQL to accept connections"
for _ in $(seq 1 30); do
  if sudo mysqladmin ping >/dev/null 2>&1; then
    echo "MySQL is ready"
    break
  fi
  sleep 1
done

echo "==> Waiting for Redis to respond"
for _ in $(seq 1 15); do
  if redis-cli ping >/dev/null 2>&1; then
    echo "Redis is ready"
    break
  fi
  sleep 1
done

echo "==> Start complete."
