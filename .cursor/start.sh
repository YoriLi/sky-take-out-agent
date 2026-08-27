#!/usr/bin/env bash
# Per-boot service reconciliation: bring up MySQL and Redis, then return.
# The backend and frontend run as long-lived processes in `terminals`.
set -euo pipefail

# Poll until MySQL answers, up to N seconds. Source of truth for readiness:
# the Debian init script gives up after a fixed 30s, which a cold
# snapshot-restored disk can exceed.
wait_for_mysql() {
  local timeout="${1:-60}"
  for _ in $(seq 1 "$timeout"); do
    if sudo mysqladmin ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

start_mysql_service() {
  sudo mkdir -p /var/run/mysqld
  sudo chown mysql:mysql /var/run/mysqld 2>/dev/null || true
  # '|| true': the init script may report failure on its own 30s timeout while
  # mysqld is still coming up (or failing); wait_for_mysql is authoritative.
  sudo service mysql start || true
}

echo "==> Starting MySQL"
start_mysql_service
if wait_for_mysql 45; then
  echo "MySQL is ready"
else
  # Environment builds restore the MySQL datadir onto an overlay filesystem's
  # lazily-loaded lower layer, where InnoDB's unconditional startup O_DIRECT
  # probe makes close() fail with EINVAL (OS error 22) and mysqld aborts.
  # Files freshly written to the writable upper layer do not have this problem
  # (a fresh apt install of MySQL on the same overlay starts fine), so rewrite
  # the datadir with a plain copy to force every file onto the upper layer,
  # then retry. This is only reached when the normal start fails.
  echo "MySQL did not start; rewriting datadir onto the overlay upper layer" >&2
  sudo service mysql stop || true
  sleep 2
  if [ -d /var/lib/mysql ]; then
    sudo rm -rf /var/lib/mysql.reinit
    sudo cp -a /var/lib/mysql /var/lib/mysql.reinit
    sudo rm -rf /var/lib/mysql
    sudo mv /var/lib/mysql.reinit /var/lib/mysql
  fi
  start_mysql_service
  if wait_for_mysql 120; then
    echo "MySQL is ready (after datadir rewrite)"
  else
    echo "ERROR: MySQL did not become ready. Recent error log:" >&2
    sudo tail -n 80 /var/log/mysql/error.log 2>&1 >&2 || true
    exit 1
  fi
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
