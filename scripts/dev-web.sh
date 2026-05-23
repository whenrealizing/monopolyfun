#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="${ROOT_DIR}/apps/web"
NEXT_LOCK="${WEB_DIR}/.next/dev/lock"

cd "$ROOT_DIR"

# shellcheck disable=SC1091
source scripts/dev-port-lib.sh
# shellcheck disable=SC1091
source scripts/env-lib.sh

load_dotenv .env

API_HOST_PORT="${API_HOST_PORT:-8080}"
# 中文注释：本机 Next 服务端请求必须走宿主机端口，覆盖 Docker Compose 场景中的 api 容器主机名。
export API_BASE_URL="http://localhost:${API_HOST_PORT}"
export NEXT_PUBLIC_API_BASE_URL="${NEXT_PUBLIC_API_BASE_URL:-http://localhost:${API_HOST_PORT}}"

WEB_PORT="${WEB_PORT:-3000}"
WEB_PORTS_TO_RELEASE="${WEB_PORTS_TO_RELEASE:-${WEB_PORT} 3001 3002}"

release_web_ports() {
  local port
  local released_ports=" "

  for port in $WEB_PORTS_TO_RELEASE; do
    if ! [[ "$port" =~ ^[0-9]+$ ]]; then
      echo "Invalid web dev port: ${port}" >&2
      exit 1
    fi

    case "$released_ports" in
      *" ${port} "*) continue ;;
    esac

    # 启动前统一释放常见 Next dev 端口，避免旧实例占用 3001/3002 后继续干扰本次启动。
    release_port "$port"
    released_ports="${released_ports}${port} "
  done
}

read_next_lock_field() {
  local field="$1"

  if [ ! -f "$NEXT_LOCK" ]; then
    return 0
  fi

  node -e '
const fs = require("fs");
const [lockPath, field] = process.argv.slice(1);
try {
  const data = JSON.parse(fs.readFileSync(lockPath, "utf8"));
  if (data[field] !== undefined && data[field] !== null) {
    process.stdout.write(String(data[field]));
  }
} catch {
  process.exit(0);
}
' "$NEXT_LOCK" "$field"
}

stop_previous_next_dev() {
  local pid
  local process_cwd
  local attempts

  pid="$(read_next_lock_field pid)"
  if [ -z "$pid" ]; then
    return 0
  fi

  if ! [[ "$pid" =~ ^[0-9]+$ ]]; then
    # lock 内容异常时清理掉，避免 Next 继续误判已有 dev 实例。
    rm -f "$NEXT_LOCK"
    return 0
  fi

  if ! kill -0 "$pid" 2>/dev/null; then
    # 旧进程已退出时删除残留 lock，让本次启动重新接管 dev 状态。
    rm -f "$NEXT_LOCK"
    return 0
  fi

  process_cwd="$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -n 1 || true)"
  if [ "$process_cwd" != "$WEB_DIR" ]; then
    # PID 被复用到其他目录时只清理 stale lock，避免误杀无关进程。
    rm -f "$NEXT_LOCK"
    return 0
  fi

  echo "Stopping previous Next dev server ${pid}"
  kill "$pid" 2>/dev/null || true

  attempts=0
  while kill -0 "$pid" 2>/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 25 ]; then
      # 正常退出超时后强制结束，保证新的 dev server 能接管当前 web 目录。
      echo "Previous Next dev server did not stop, forcing shutdown"
      kill -9 "$pid" 2>/dev/null || true
      break
    fi
    sleep 0.2
  done

  rm -f "$NEXT_LOCK"
}

stop_previous_next_dev
release_web_ports

cd "$WEB_DIR"
# 中文注释：本地 dev 固定使用 webpack，避开 Turbopack .sst 缓存写入被 IDE 清理后卡住的问题。
exec next dev --webpack --port "$WEB_PORT"
