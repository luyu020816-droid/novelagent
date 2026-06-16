#!/usr/bin/env bash
# 本地栈烟囱测试：Writer/Java 健康检查 + Python 离线单测
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WRITER_URL="${WRITER_URL:-http://127.0.0.1:8000}"
JAVA_URL="${JAVA_URL:-http://127.0.0.1:8080}"

echo "== Python unit tests (offline) =="
cd "$ROOT/writer-python"
PY="${ROOT}/writer-python/.venv/bin/python"
if [[ ! -x "$PY" ]]; then
  PY=python3
fi
"$PY" -m unittest discover -s tests -p "test_*.py" -v
echo ""
echo "== Offline eval (golden) =="
"$PY" scripts/eval_critic_dimensions.py --dir fixtures/eval/golden --schema-only

echo ""
echo "== Writer health =="
if curl -sf "$WRITER_URL/api/writer/health" >/dev/null; then
  echo "OK $WRITER_URL"
else
  echo "SKIP Writer not up at $WRITER_URL"
fi

echo ""
echo "== Java health =="
if curl -sf "$JAVA_URL/actuator/health" >/dev/null 2>&1 || curl -sf "$JAVA_URL/api/projects" -o /dev/null -w "%{http_code}" | grep -qE '^(200|401|403)$'; then
  echo "OK $JAVA_URL (reachable)"
else
  echo "SKIP Java not up at $JAVA_URL"
fi

echo ""
echo "smoke_stack done"
