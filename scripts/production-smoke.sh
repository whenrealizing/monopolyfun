#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# shellcheck disable=SC1091
source scripts/env-lib.sh

load_dotenv .env

failures=0
require_true() {
  local name="$1"
  local value="${!name:-}"
  if [ "$value" = "true" ]; then
    echo "ok $name=true"
  else
    echo "fail $name must be true"
    failures=$((failures + 1))
  fi
}

require_present() {
  local name="$1"
  local value="${!name:-}"
  if [ -n "$value" ]; then
    echo "ok $name"
  else
    echo "fail $name is required"
    failures=$((failures + 1))
  fi
}

optional_present() {
  local name="$1"
  local value="${!name:-}"
  if [ -n "$value" ]; then
    echo "ok $name"
  else
    echo "ok $name optional"
  fi
}

require_not_value() {
  local name="$1"
  local forbidden="$2"
  local value="${!name:-}"
  if [ -n "$value" ] && [ "$value" != "$forbidden" ]; then
    echo "ok $name"
  else
    echo "fail $name must be rotated"
    failures=$((failures + 1))
  fi
}

# 中文注释：脚本与 ProductionSecurityGuard 使用同一组生产约束，部署前可在本地或 CI fail-fast。
require_true APP_PRODUCTION
require_true COOKIE_SECURE
require_not_value PAYMENT_PROVIDER fake
require_not_value UPLOAD_PROVIDER fake
require_not_value PAYMENT_CALLBACK_SECRET monopolyfun-dev-secret
require_not_value DIGITAL_INVENTORY_ENCRYPTION_SECRET monopolyfun-digital-inventory-dev-secret
require_present DIGITAL_INVENTORY_ENCRYPTION_SECRET
require_not_value DATABASE_PASSWORD postgres
require_present UPLOAD_BUCKET
require_present UPLOAD_BASE_URL
require_present DATABASE_URL

payment_callback_secret="${PAYMENT_CALLBACK_SECRET:-}"
digital_inventory_secret="${DIGITAL_INVENTORY_ENCRYPTION_SECRET:-}"
if [ "${#payment_callback_secret}" -lt 24 ]; then
  echo "fail PAYMENT_CALLBACK_SECRET must be at least 24 characters"
  failures=$((failures + 1))
else
  echo "ok PAYMENT_CALLBACK_SECRET length"
fi

if [ "${#digital_inventory_secret}" -lt 24 ]; then
  echo "fail DIGITAL_INVENTORY_ENCRYPTION_SECRET must be at least 24 characters"
  failures=$((failures + 1))
else
  echo "ok DIGITAL_INVENTORY_ENCRYPTION_SECRET length"
fi

if printf '%s' "${DATABASE_URL:-}" | grep -Eq 'localhost|127\.0\.0\.1'; then
  echo "fail DATABASE_URL must point to an external PostgreSQL service"
  failures=$((failures + 1))
else
  echo "ok DATABASE_URL"
fi

for callback_name in GITHUB_REDIRECT_URI GITHUB_VERIFICATION_REDIRECT_URI GITHUB_WEB_CALLBACK_URL; do
  if printf '%s' "${!callback_name:-}" | grep -Eq 'localhost|127\.0\.0\.1'; then
    echo "fail ${callback_name} must use a production domain"
    failures=$((failures + 1))
  else
    echo "ok ${callback_name}"
  fi
done

case "${UPLOAD_PROVIDER:-}" in
  s3|s3-compatible|r2)
    require_present UPLOAD_ACCESS_KEY_ID
    require_present UPLOAD_SECRET_ACCESS_KEY
    require_present UPLOAD_ENDPOINT
    ;;
esac

case "${PAYMENT_PROVIDER:-}" in
  okx)
    require_present OKX_ONCHAIN_PAY_API_KEY
    require_present OKX_ONCHAIN_PAY_API_SECRET
    # 中文注释：OKX x402 客户端只在配置 project id 时发送项目头，smoke 跟随运行时契约做可选校验。
    optional_present OKX_ONCHAIN_PAY_PROJECT_ID
    ;;
esac

if [ "$failures" -gt 0 ]; then
  exit 1
fi

echo "production smoke passed"
