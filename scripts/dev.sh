#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck disable=SC1091
source scripts/dev-port-lib.sh
# shellcheck disable=SC1091
source scripts/env-lib.sh

load_dotenv .env

SERVER_PORT="${SERVER_PORT:-${API_HOST_PORT:-8080}}"

api_pid=""
web_pid=""
ready_pid=""

is_running_job() {
  local pid="$1"
  jobs -pr | grep -q "^${pid}$"
}

cleanup() {
  local exit_code=$?

  for pid in "${ready_pid}" "${web_pid}" "${api_pid}"; do
    if [ -n "${pid}" ] && is_running_job "${pid}"; then
      kill "${pid}" 2>/dev/null || true
    fi
  done

  wait 2>/dev/null || true
  exit "$exit_code"
}

trap cleanup EXIT INT TERM

# 本地 dev 依赖 Docker Postgres，先做依赖门禁，避免启动编排进入健康检查空等。
bash scripts/require-docker.sh

# 中文注释：源码 API 接管 8080 前先停同一 Compose 项目的 api，避免健康检查命中旧容器进程。
if [[ "$(docker compose port api 8080 2>/dev/null || true)" == *":${SERVER_PORT}" ]]; then
  docker compose stop api >/dev/null
fi

# 启动前先释放后端端口，避免健康检查命中旧 API 进程。
release_port "${SERVER_PORT}"

pnpm api:dev &
api_pid=$!

# 后端启动失败时立即结束本地 dev 编排，避免健康检查继续空等。
bash scripts/wait-api-ready.sh &
ready_pid=$!

while is_running_job "${ready_pid}"; do
  if ! is_running_job "${api_pid}"; then
    wait "${api_pid}"
    exit $?
  fi
  sleep 1
done

wait "${ready_pid}"
ready_pid=""

# 前端只在后端健康检查通过后启动，保持本地 dev 和 Docker 依赖顺序一致。
pnpm web:dev &
web_pid=$!

while true; do
  if [ -n "${api_pid}" ] && ! is_running_job "${api_pid}"; then
    wait "${api_pid}"
    exit $?
  fi

  if [ -n "${web_pid}" ] && ! is_running_job "${web_pid}"; then
    wait "${web_pid}"
    exit $?
  fi

  sleep 1
done
