# 本地栈烟囱测试：Writer/Java 健康检查 + Python 离线单测
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$WriterUrl = if ($env:WRITER_URL) { $env:WRITER_URL } else { "http://127.0.0.1:8000" }
$JavaUrl = if ($env:JAVA_URL) { $env:JAVA_URL } else { "http://127.0.0.1:8080" }

Write-Host "== Python unit tests (offline) =="
$Py = Join-Path $Root "writer-python\.venv\Scripts\python.exe"
if (-not (Test-Path $Py)) { $Py = "python" }
Push-Location (Join-Path $Root "writer-python")
& $Py -m unittest discover -s tests -p "test_*.py" -v
Write-Host ""
Write-Host "== Offline eval (golden) =="
& $Py scripts/eval_critic_dimensions.py --dir fixtures/eval/golden --schema-only
Pop-Location

Write-Host ""
Write-Host "== Writer health =="
try {
  Invoke-WebRequest -Uri "$WriterUrl/api/writer/health" -UseBasicParsing -TimeoutSec 3 | Out-Null
  Write-Host "OK $WriterUrl"
} catch {
  Write-Host "SKIP Writer not up at $WriterUrl"
}

Write-Host ""
Write-Host "== Java health =="
try {
  Invoke-WebRequest -Uri "$JavaUrl/actuator/health" -UseBasicParsing -TimeoutSec 3 | Out-Null
  Write-Host "OK $JavaUrl"
} catch {
  try {
    Invoke-WebRequest -Uri "$JavaUrl/api/projects" -UseBasicParsing -TimeoutSec 3 | Out-Null
    Write-Host "OK $JavaUrl (reachable)"
  } catch {
    Write-Host "SKIP Java not up at $JavaUrl"
  }
}

Write-Host ""
Write-Host "smoke_stack done"
