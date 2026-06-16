#!/usr/bin/env bash
# Day 15：本地一键拉起依赖与进程（需已安装 Docker、JDK17、Maven、Node、Python 3.11+）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

export MYTHOSFORGE_INTERNAL_TOKEN="${MYTHOSFORGE_INTERNAL_TOKEN:-dev-internal-token}"

echo "==> Docker compose (postgres redis qdrant neo4j)"
docker compose up -d postgres redis qdrant neo4j

cleanup() {
  echo ""
  echo "==> Stopping background jobs..."
  for pid in $(jobs -p); do kill "$pid" 2>/dev/null || true; done
}
trap cleanup EXIT INT TERM

PY_DIR="$ROOT/writer-python"
if [[ -f "$PY_DIR/.venv/bin/activate" ]]; then
  # shellcheck source=/dev/null
  source "$PY_DIR/.venv/bin/activate"
fi

echo "==> Spring Boot (8080)"
(cd "$ROOT/backend-java" && mvn -q spring-boot:run) &

echo "==> Writer FastAPI (8000)"
(cd "$PY_DIR" && uvicorn app.main:app --host 0.0.0.0 --port 8000) &

echo "==> Frontend Vite (5173)"
(cd "$ROOT/frontend" && npm run dev) &

echo ""
echo "就绪后访问：前端 http://localhost:5173"
echo "  Setup: /projects/{id}/setup  |  DAG: /projects/{id}/workflow"
echo "章节异步生成由 Java 线程池直接调用 Writer（:8000），请保持 uvicorn 运行。"
echo "内部回调令牌 MYTHOSFORGE_INTERNAL_TOKEN=$MYTHOSFORGE_INTERNAL_TOKEN（须与 Java application.yml 一致）"
echo "按 Ctrl+C 结束本脚本将尝试杀掉后台任务。"
wait
