#!/usr/bin/env bash

load_dotenv() {
  local env_file="${1:-.env}"
  local line key value

  [ -f "$env_file" ] || return 0

  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*($|#) ]] && continue

    key="${line%%=*}"
    value="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"

    if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid dotenv key in ${env_file}: ${key}" >&2
      return 1
    fi

    # 中文注释：显式环境变量优先于 .env，CI 和生产 smoke 可以注入真实配置覆盖本地默认值。
    if [[ -n "${!key+x}" ]]; then
      continue
    fi

    # 中文注释：使用 export 的赋值形式保留空格、私钥和转义换行，避免 shell source 把配置内容当命令执行。
    if [[ "$value" == \"*\" && "$value" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi

    export "${key}=${value}"
  done < "$env_file"
}
