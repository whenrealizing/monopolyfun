#!/usr/bin/env bash
set -euo pipefail

export API_PORT="${API_PORT:-18080}"
export SERVER_PORT="${SERVER_PORT:-${API_PORT}}"
export API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:${API_PORT}}"
export NEXT_PUBLIC_API_BASE_URL="${NEXT_PUBLIC_API_BASE_URL:-}"
export DATABASE_URL="${DATABASE_URL:-}"
export DATABASE_USERNAME="${DATABASE_USERNAME:-}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-}"
export APP_PRODUCTION="${APP_PRODUCTION:-true}"
export COOKIE_SECURE="${COOKIE_SECURE:-true}"
# 中文注释：Railway 会注入容器 hostname，Next 需要绑定 0.0.0.0 才能被边缘代理访问。
export HOSTNAME="${APP_HOSTNAME:-0.0.0.0}"
export PORT="${PORT:-3000}"

if [ -z "$DATABASE_URL" ] || [ -z "$DATABASE_USERNAME" ] || [ -z "$DATABASE_PASSWORD" ]; then
  echo "DATABASE_URL, DATABASE_USERNAME, and DATABASE_PASSWORD are required" >&2
  exit 1
fi

if [ "${APP_PRODUCTION:-false}" = "true" ] && [[ "$DATABASE_URL" =~ (localhost|127\.0\.0\.1) ]]; then
  # 中文注释：生产环境必须连接独立数据库服务，防止 deployment 替换容器时丢失库文件。
  echo "APP_PRODUCTION=true requires an external PostgreSQL DATABASE_URL" >&2
  exit 1
fi

java -jar /app/api/app.jar &
api_pid="$!"

until curl -fsS "http://127.0.0.1:${API_PORT}/actuator/health" >/dev/null 2>&1; do
  if ! kill -0 "$api_pid" >/dev/null 2>&1; then
    echo "API process exited before becoming healthy" >&2
    exit 1
  fi
  sleep 2
done

# 中文注释：公网只暴露 Next 端口；API 经 Next rewrite 走同源 /api，减少跨域和多域名配置。
exec node /app/web/apps/web/server.js
