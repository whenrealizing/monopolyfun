#!/usr/bin/env bash

release_port() {
  local port="$1"
  local pids
  local pid
  local releasable_pids=""
  local process_command

  pids="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
  if [ -z "${pids}" ]; then
    return 0
  fi

  for pid in ${pids}; do
    process_command="$(ps -p "${pid}" -o comm= -o command= 2>/dev/null || true)"
    case "${process_command}" in
      *com.docker*|*Docker.app*)
        # 中文注释：Docker 端口代理可能同时承载 Postgres 等映射，dev 清端口不能杀掉 Docker 后端。
        echo "Skipping Docker-owned port ${port}: ${pid}"
        ;;
      *)
        releasable_pids="${releasable_pids} ${pid}"
        ;;
    esac
  done

  if [ -z "${releasable_pids}" ]; then
    return 0
  fi

  echo "Releasing port ${port}:${releasable_pids}"
  kill ${releasable_pids}

  local attempts=0
  while has_releasable_port_listener "${port}"; do
    attempts=$((attempts + 1))
    if [ "${attempts}" -ge 25 ]; then
      # 端口释放超时后强制清理，保证 dev 启动绑定到当前进程。
      echo "Port ${port} release timeout, forcing shutdown"
      kill -9 ${releasable_pids} 2>/dev/null || true
      break
    fi
    sleep 0.2
  done
}

has_releasable_port_listener() {
  local port="$1"
  local pid
  local process_command

  for pid in $(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true); do
    process_command="$(ps -p "${pid}" -o comm= -o command= 2>/dev/null || true)"
    case "${process_command}" in
      *com.docker*|*Docker.app*) ;;
      *) return 0 ;;
    esac
  done

  return 1
}
