#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck disable=SC1091
source scripts/env-lib.sh

load_dotenv .env

SERVER_PORT="${SERVER_PORT:-${API_HOST_PORT:-8080}}"
API_HEALTH_URL="${API_HEALTH_URL:-http://localhost:${SERVER_PORT}/actuator/health}"
MAX_ATTEMPTS="${API_READY_MAX_ATTEMPTS:-120}"

# 后端健康门禁：API 完成启动和健康检查后再启动前端，避免首屏请求打到初始化中的服务。
for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  if curl -fsS "$API_HEALTH_URL" >/dev/null 2>&1; then
    echo "API ready: ${API_HEALTH_URL}"
    exit 0
  fi

  echo "Waiting for API (${attempt}/${MAX_ATTEMPTS}): ${API_HEALTH_URL}"
  sleep 1
done

# 明确失败路径：后端长期未就绪时让 dev 编排脚本结束整组进程。
echo "API readiness timeout: ${API_HEALTH_URL}" >&2
exit 1
