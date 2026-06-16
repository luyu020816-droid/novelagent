#!/usr/bin/env bash
# MythosForge 一键启动（Linux / macOS / Git Bash）
# Windows PowerShell 请用同目录 start.ps1
#
# 拉起：Docker（postgres / redis / qdrant / neo4j）+ Java :8080 + Writer :8000 + 前端 :5173
# 前置：writer-python/.env 含 OPENAI_API_KEY；MYTHOSFORGE_INTERNAL_TOKEN 与 Java 一致
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$ROOT/run_local.sh" "$@"
