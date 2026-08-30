#!/usr/bin/env bash
# 便捷启动脚本：构建 + 启动整套 Docker 环境
# 用法: ./scripts/up.sh [--build] [target]
cd "$(dirname "$0")/.."

case "${1:-}" in
  --build)
    docker compose -f docker/compose.yaml build
    ;;
  *)
    ;;
esac

echo "==> starting maccms + mysql + emulator + build containers"
docker compose -f docker/compose.yaml up -d

echo "==> logs follow (Ctrl+C to detach)"
docker compose -f docker/compose.yaml logs -f maccms mysql emulator build | tail -100
