#!/usr/bin/env bash
# Per-boot service reconciliation: bring up MySQL and Redis, then return.
# The backend and frontend run as long-lived processes in `terminals`.
#
# NOTE: the Debian init script for MySQL gives up after a fixed 30s ping
# timeout. On a snapshot-restored disk the very first mysqld start can read
# its datadir slowly (cold, lazily-loaded blocks) and exceed that window, so
# we ignore the init script's own timeout and poll readiness ourselves with a
# generous timeout instead.
set -euo pipefail

wait_for_mysql() {
  for _ in $(seq 1 180); do
    if sudo mysqladmin ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

echo "==> Ensuring MySQL runtime directory exists"
sudo mkdir -p /var/run/mysqld
sudo chown mysql:mysql /var/run/mysqld 2>/dev/null || true

echo "==> Starting MySQL"
# '|| true': the init script may report failure on its 30s timeout while
# mysqld is still coming up; the poll below is the source of truth.
sudo service mysql start || true
if wait_for_mysql; then
  echo "MySQL is ready"
else
  echo "ERROR: MySQL did not become ready. Recent error log:" >&2
  sudo tail -n 80 /var/log/mysql/error.log 2>&1 >&2 || true
  exit 1
fi

echo "==> Starting Redis"
sudo service redis-server start || true
for _ in $(seq 1 30); do
  redis-cli ping >/dev/null 2>&1 && break
  sleep 1
done
if redis-cli ping >/dev/null 2>&1; then
  echo "Redis is ready"
else
  echo "ERROR: Redis did not become ready." >&2
  exit 1
fi

echo "==> Start complete."
