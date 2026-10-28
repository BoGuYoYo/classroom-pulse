#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY="$ROOT/deploy"
cd "$DEPLOY"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$DEPLOY/backups/$STAMP"
mkdir -p "$OUT"

echo "==> 备份 MySQL"
docker compose --env-file .env exec -T mysql sh -c \
  'exec mysqldump --single-transaction --routines --triggers -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip > "$OUT/mysql.sql.gz"

echo "==> 备份作业图片"
docker compose --env-file .env exec -T backend tar -czf - -C /data uploads > "$OUT/uploads.tar.gz"

echo "==> 校验备份可读性"
gzip -t "$OUT/mysql.sql.gz"
tar -tzf "$OUT/uploads.tar.gz" >/dev/null

find "$DEPLOY/backups" -mindepth 1 -maxdepth 1 -type d -mtime +30 -exec rm -rf {} +
echo "备份完成：$OUT"
