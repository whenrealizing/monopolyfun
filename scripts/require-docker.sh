#!/usr/bin/env bash
set -euo pipefail

# 中文注释：本地数据库和集成测试都依赖可用 Docker daemon，直接校验 docker info，兼容 Docker Desktop 的用户 socket。
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  exit 0
fi

socket_candidates=(
  "/var/run/docker.sock"
  "${HOME}/.docker/run/docker.sock"
)

for socket in "${socket_candidates[@]}"; do
  if [[ -S "$socket" ]]; then
    echo "Docker daemon is unreachable through $socket; start Docker Desktop and retry" >&2
    exit 1
  fi
done

echo "Docker daemon is required; start Docker Desktop and retry" >&2
exit 1
