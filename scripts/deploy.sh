#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY="$ROOT/deploy"
cd "$DEPLOY"

if [[ ! -f .env ]]; then
  echo "缺少 deploy/.env，请先复制 .env.example 并填写。" >&2
  exit 1
fi

DOMAIN="$(grep -E '^DOMAIN=' .env | head -n1 | cut -d= -f2- | tr -d '\r')"
if [[ -z "$DOMAIN" ]]; then
  echo "deploy/.env 中缺少 DOMAIN。" >&2
  exit 1
fi

if [[ ! -f "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ]]; then
  echo "未找到证书 /etc/letsencrypt/live/$DOMAIN/fullchain.pem。" >&2
  echo "请先在该服务器执行：certbot certonly --standalone -d $DOMAIN" >&2
  exit 1
fi

echo "==> 构建并启动 Classroom Pulse ($DOMAIN)"
docker compose --env-file .env up -d --build

echo "==> 等待服务健康"
for _ in $(seq 1 30); do
  if curl -fsS "https://$DOMAIN/healthz" >/dev/null 2>&1; then
    echo "部署完成：https://$DOMAIN"
    docker compose --env-file .env ps
    exit 0
  fi
  sleep 3
done

echo "健康检查超时，请查看日志：docker compose --env-file .env logs --tail=200" >&2
docker compose --env-file .env ps
exit 1
