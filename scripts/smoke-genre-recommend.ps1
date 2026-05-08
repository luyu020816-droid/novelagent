# Genre recommend smoke: empty body vs UTF-8 JSON; optional Java hop.
# Usage:
#   .\scripts\smoke-genre-recommend.ps1 -ProbeOnly
#   .\scripts\smoke-genre-recommend.ps1 -ProjectId "b100fda4131e49299b2c8905a0437667"
#
param(
    [string] $ProjectId = "",
    [string] $JavaUrl = "http://localhost:8080",
    [string] $WriterUrl = "http://127.0.0.1:8000",
    [switch] $ProbeOnly,
    [int] $WriterTimeoutSec = 180
)

$ErrorActionPreference = "Continue"

function Invoke-HttpRequestCapture {
    param(
        [string] $Uri,
        [string] $Method,
        [string] $ContentType,
        $Body
    )
    try {
        $r = Invoke-WebRequest -Uri $Uri -Method $Method -ContentType $ContentType `
            -Body $Body -UseBasicParsing -TimeoutSec $WriterTimeoutSec
        return @{ Ok = $true; Status = [int]$r.StatusCode; Content = $r.Content }
    }
    catch {
        $err = $_
        $ex = $err.Exception
        if ($err.ErrorDetails -and $err.ErrorDetails.Message) {
            $code = 0
            if ($null -ne $ex.Response) {
                $code = [int]$ex.Response.StatusCode
            }
            return @{ Ok = $false; Status = $code; Content = $err.ErrorDetails.Message }
        }
        $resp = $ex.Response
        if ($null -ne $resp) {
            try {
                $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $txt = $sr.ReadToEnd()
                return @{ Ok = $false; Status = [int]$resp.StatusCode; Content = $txt }
            }
            catch {
                return @{ Ok = $false; Status = [int]$resp.StatusCode; Content = $ex.Message }
            }
        }
        return @{ Ok = $false; Status = 0; Content = $ex.Message }
    }
}

$minimalBody = @{
    targetPlatform   = "Fanqie"
    genderChannel    = "male"
    preferredGenres  = @()
    avoid            = @()
    writingStrength  = @()
    riskPreference   = "medium"
}
if ($ProjectId) {
    $minimalBody.projectId = $ProjectId
}
$jsonMinimal = $minimalBody | ConvertTo-Json -Compress -Depth 6

Write-Host "=== A) Writer POST empty body (expect 422 body-level missing) ===" -ForegroundColor Cyan
$emptyUrl = "$WriterUrl/api/writer/genre/recommend"
$a = Invoke-HttpRequestCapture -Uri $emptyUrl -Method POST -ContentType "application/json; charset=utf-8" -Body ([byte[]]@())
Write-Host "Status:" $a.Status
Write-Host $a.Content

Write-Host ""
Write-Host "=== B) Writer POST minimal JSON UTF-8 ===" -ForegroundColor Cyan
Write-Host "payload chars:" $jsonMinimal.Length
$b = Invoke-HttpRequestCapture -Uri $emptyUrl -Method POST -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($jsonMinimal))
Write-Host "Status:" $b.Status
if ($b.Content.Length -gt 1200) {
    Write-Host ($b.Content.Substring(0, 1200))
    Write-Host "... [truncated]"
}
else {
    Write-Host $b.Content
}

if ($ProbeOnly) {
    Write-Host ""
    Write-Host "ProbeOnly done (Java skipped)." -ForegroundColor Yellow
    exit 0
}

if (-not $ProjectId) {
    Write-Host ""
    Write-Host "No -ProjectId: skip Java. Example: -ProjectId b100fda4131e49299b2c8905a0437667" -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "=== C) Java proxy (502 body should include writer-python message) ===" -ForegroundColor Cyan
$javaUri = "$JavaUrl/api/projects/$ProjectId/genre/recommend"
$c = Invoke-HttpRequestCapture -Uri $javaUri -Method POST -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($jsonMinimal))
Write-Host "Status:" $c.Status
Write-Host $c.Content
