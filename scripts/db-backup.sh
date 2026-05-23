#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/db-lib.sh"

mkdir -p backups
STAMP="$(date +"%Y%m%d-%H%M%S")"
OUT_FILE="${1:-backups/monopolyfun-${STAMP}.sql}"

pg_dump_cmd --format=plain --no-owner --no-privileges > "$OUT_FILE"
echo "backup written to $OUT_FILE"
