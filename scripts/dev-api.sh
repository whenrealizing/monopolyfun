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
POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-55432}"
# 中文注释：dev 后端固定连接本地 Postgres，关闭生产守卫；支付和上传 provider 仍沿用 .env 的真实配置。
export APP_PRODUCTION="${APP_PRODUCTION_LOCAL:-false}"
export COOKIE_SECURE="${COOKIE_SECURE_LOCAL:-false}"
export DATABASE_URL="${DATABASE_URL_LOCAL:-jdbc:postgresql://localhost:${POSTGRES_HOST_PORT}/monopolyfun}"
export DATABASE_USERNAME="${DATABASE_USERNAME:-postgres}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-postgres}"
export SERVER_PORT

ensure_local_postgres() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "docker is required to start local Postgres" >&2
    exit 1
  fi

  # 中文注释：先校验 Docker daemon 可连接，让本地 API 启动在依赖缺失时快速失败。
  bash scripts/require-docker.sh

  # 中文注释：API dev 是 Playwright 的上游服务，先等 Postgres healthy，避免页面测试撞上数据库重启窗口。
  docker compose up -d postgres >/dev/null
  for _ in $(seq 1 45); do
    local status
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' monopolyfun-postgres 2>/dev/null || true)"
    if [ "${status}" = "healthy" ] && (echo >"/dev/tcp/127.0.0.1/${POSTGRES_HOST_PORT}") >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  echo "monopolyfun-postgres did not become healthy on port ${POSTGRES_HOST_PORT}" >&2
  docker compose ps postgres >&2 || true
  exit 1
}

stop_compose_api_on_dev_port() {
  local published

  published="$(docker compose port api 8080 2>/dev/null || true)"
  case "${published}" in
    *":${SERVER_PORT}")
      # 中文注释：本地 API 以源码进程接管端口，先停掉同一 Compose 项目的 api 服务，避免误杀 Docker Desktop 端口代理。
      docker compose stop api >/dev/null
      ;;
  esac
}

ensure_local_postgres
stop_compose_api_on_dev_port
release_port "${SERVER_PORT}"

exec mvn -f apps/api/pom.xml spring-boot:run
