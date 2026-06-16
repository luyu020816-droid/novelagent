# MythosForge 一键启动（Windows PowerShell）
# 拉起：Docker + Java :8080 + Writer :8000 + 前端 :5173
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
Set-Location $Root

if (-not $env:MYTHOSFORGE_INTERNAL_TOKEN) {
    $env:MYTHOSFORGE_INTERNAL_TOKEN = "dev-internal-token"
}

Write-Host "==> Docker compose (postgres redis qdrant neo4j)"
docker compose up -d postgres redis qdrant neo4j

$PyDir = Join-Path $Root "writer-python"
$VenvPy = Join-Path $PyDir ".venv\Scripts\python.exe"
if (-not (Test-Path $VenvPy)) {
    Write-Host "==> Creating Python venv..."
    & python -m venv (Join-Path $PyDir ".venv")
    & $VenvPy -m pip install -q -r (Join-Path $PyDir "requirements.txt")
}

$JavaDir = Join-Path $Root "backend-java"
$FrontDir = Join-Path $Root "frontend"

Write-Host "==> Starting Java (8080), Writer (8000), Frontend (5173) in new windows..."
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "Set-Location '$JavaDir'; `$env:MYTHOSFORGE_INTERNAL_TOKEN='$($env:MYTHOSFORGE_INTERNAL_TOKEN)'; mvn spring-boot:run"
)
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "Set-Location '$PyDir'; `$env:MYTHOSFORGE_INTERNAL_TOKEN='$($env:MYTHOSFORGE_INTERNAL_TOKEN)'; & '$VenvPy' -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000"
)
Start-Sleep -Seconds 1
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "Set-Location '$FrontDir'; if (-not (Test-Path node_modules)) { npm install }; npm run dev"
)

Write-Host ""
Write-Host "就绪后访问："
Write-Host "  前端     http://localhost:5173"
Write-Host "  Setup    http://localhost:5173/projects/{id}/setup"
Write-Host "  DAG 画布 http://localhost:5173/projects/{id}/workflow"
Write-Host "  Java API http://localhost:8080"
Write-Host "  Writer   http://localhost:8000"
Write-Host ""
Write-Host "章节生成由 Java 调 Writer；Flyway 在 Java 启动时自动迁移（含 project_dag_versions）。"
Write-Host "烟囱测试: .\scripts\smoke_stack.ps1"
Write-Host "关闭各 PowerShell 窗口即可停止对应服务。"
