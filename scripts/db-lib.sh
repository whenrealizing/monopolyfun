#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck disable=SC1091
source scripts/env-lib.sh

load_dotenv .env

# 中文注释：维护脚本默认跟随 Compose 宿主机端口，保证与 Spring 本地 profile 和 jOOQ codegen 一致。
DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:55432/monopolyfun}"
DATABASE_USERNAME="${DATABASE_USERNAME:-postgres}"
DATABASE_PASSWORD="${DATABASE_PASSWORD:-postgres}"

if [[ "$DATABASE_URL" != jdbc:postgresql://* ]]; then
  echo "DATABASE_URL must use jdbc:postgresql://host:port/db format" >&2
  exit 1
fi

DB_URL_NO_PREFIX="${DATABASE_URL#jdbc:postgresql://}"
DB_HOST_PORT="${DB_URL_NO_PREFIX%%/*}"
DB_NAME="${DB_URL_NO_PREFIX#*/}"
DB_HOST="${DB_HOST_PORT%%:*}"
DB_PORT="${DB_HOST_PORT##*:}"

export PGPASSWORD="$DATABASE_PASSWORD"

psql_cmd() {
  # 中文注释：本机未安装 psql 时复用项目 Postgres 容器，保证种子和维护脚本在标准 Docker 开发环境可运行。
  if command -v psql >/dev/null 2>&1; then
    psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" "$@"
    return
  fi
  docker exec -i -e PGPASSWORD="$DATABASE_PASSWORD" monopolyfun-postgres \
    psql -v ON_ERROR_STOP=1 -U "$DATABASE_USERNAME" -d "$DB_NAME" "$@"
}

pg_dump_cmd() {
  # 中文注释：备份脚本同样跟随容器化数据库入口，避免维护命令依赖宿主机客户端。
  if command -v pg_dump >/dev/null 2>&1; then
    pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" "$@"
    return
  fi
  docker exec -i -e PGPASSWORD="$DATABASE_PASSWORD" monopolyfun-postgres \
    pg_dump -U "$DATABASE_USERNAME" -d "$DB_NAME" "$@"
}
