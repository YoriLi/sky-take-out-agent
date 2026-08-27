#!/usr/bin/env bash
# 本地/单机「先跑起来」脚本：加载 env 文件后启动后端 jar。
# 用法：
#   cp deploy/sky-server.env.example deploy/sky-server.env   # 然后填真实值
#   bash deploy/run-local.sh [jar路径]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/sky-server.env"
JAR="${1:-${SCRIPT_DIR}/../sky-take-out/sky-server/target/sky-server-1.0-SNAPSHOT.jar}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "缺少 ${ENV_FILE}，请先 cp sky-server.env.example sky-server.env 并填值" >&2
  exit 1
fi
if [[ ! -f "${JAR}" ]]; then
  echo "找不到 jar：${JAR}" >&2
  echo "先构建：cd sky-take-out && mvn -pl sky-server -am clean package -DskipTests" >&2
  exit 1
fi

# 将 env 文件中的变量导出为进程环境变量后启动
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

echo "启动后端，端口 8080 ..."
exec java -jar "${JAR}"
