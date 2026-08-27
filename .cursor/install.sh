#!/usr/bin/env bash
# Idempotent setup for the Sky Take-out (苍穹外卖) full stack:
# MySQL + Redis + Spring Boot backend + Vue admin frontend.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

JDK11="/usr/lib/jvm/java-11-openjdk-amd64"
NODE_MAJOR=14

echo "==> Installing system packages (MySQL, Redis, Maven, JDK 11)"
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq
# JDK 11 is required only to COMPILE the backend: lombok 1.18.20 (pinned) does
# not support newer JDKs. The compiled jar runs fine on the image default JDK.
sudo apt-get install -y -qq mysql-server redis-server maven openjdk-11-jdk

echo "==> Configuring InnoDB flush method for snapshot-restored overlay FS"
# Cloud Agent environment builds restore the MySQL datadir onto a lazily-loaded
# overlay filesystem where InnoDB's default O_DIRECT I/O fails with EINVAL
# (OS error 22), so mysqld cannot start from the snapshot. Using fsync avoids
# O_DIRECT and lets MySQL start reliably from the restored datadir.
sudo mkdir -p /etc/mysql/mysql.conf.d
sudo tee /etc/mysql/mysql.conf.d/zz-cloud-agent.cnf >/dev/null <<'CNF'
[mysqld]
innodb_flush_method = fsync
CNF

echo "==> Starting MySQL + Redis (needed to seed the schema during install)"
# Reuse the robust, poll-based service startup so a cold snapshot-restored
# disk cannot trip the init script's fixed 30s timeout.
"$(dirname "${BASH_SOURCE[0]}")/start.sh"

echo "==> Ensuring root MySQL password (matches application-dev config default)"
if mysql -uroot -p123456 -h 127.0.0.1 -e "SELECT 1" >/dev/null 2>&1; then
  MYSQL=(mysql -uroot -p123456 -h 127.0.0.1)
else
  sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '123456'; FLUSH PRIVILEGES;"
  MYSQL=(mysql -uroot -p123456 -h 127.0.0.1)
fi

echo "==> Loading database schema + seed data (idempotent: drops & recreates tables)"
"${MYSQL[@]}" < database/sky.sql
"${MYSQL[@]}" sky_take_out < database/security_hardening.sql

echo "==> Creating backend dev config from template (if missing)"
DEV_YML="sky-take-out/sky-server/src/main/resources/application-dev.yml"
if [ ! -f "$DEV_YML" ]; then
  cp "${DEV_YML}.example" "$DEV_YML"
fi

echo "==> Building backend with JDK 11 (lombok compatibility)"
export JAVA_HOME="$JDK11"
( cd sky-take-out && mvn -q clean package -DskipTests )

echo "==> Installing frontend dependencies with Node ${NODE_MAJOR} via nvm"
# This legacy Vue CLI 3 / webpack 4 app does not build on the image default Node.
export NVM_DIR="$HOME/.nvm"
# shellcheck disable=SC1091
. "$NVM_DIR/nvm.sh"
nvm install "$NODE_MAJOR" >/dev/null
nvm use "$NODE_MAJOR" >/dev/null
# Pin Node 14 on PATH so npm resolves to it regardless of other shims.
export PATH="$(dirname "$(nvm which "$NODE_MAJOR")"):$PATH"
(
  cd project-rjwm-admin-vue-ts
  # fibers is an unused, optional native accelerator that fails to compile on
  # modern Python/node-gyp; skip native builds and drop it so sass-loader falls
  # back to async dart-sass.
  npm ci --ignore-scripts --no-audit --no-fund
  rm -rf node_modules/fibers
)

echo "==> Stopping MySQL so the base image is snapshotted in a clean state"
# Environment builds snapshot the VM right after install. Leaving MySQL running
# would capture an unclean datadir that a future boot may fail to start; a clean
# shutdown here lets start.sh reliably start it from the snapshot.
sudo service mysql stop || true

echo "==> Install complete."
