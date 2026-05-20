# distribute.ps1 — Build debug APK and send to Elly via Firebase App Distribution.
#
# Usage:
#   .\scripts\distribute.ps1
#
# Before running:
#   1. Edit app\release-notes.txt with what's new in this build.
#   2. Make sure local.properties has firebase.appId and firebase.testers filled in.
#   3. Run `firebase login` once if you haven't already (npm install -g firebase-tools).
#
# The script stops immediately if any step fails.
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot | Split-Path -Parent
Push-Location $ProjectRoot

try {
    # ── Read local.properties ──────────────────────────────────────────────────
    $propsFile = Join-Path $ProjectRoot "local.properties"
    if (-not (Test-Path $propsFile)) {
        throw "local.properties not found. Expected at: $propsFile"
    }

    $props = @{}
    Get-Content $propsFile | Where-Object { $_ -notmatch '^\s*[#!]' -and $_ -match '=' } | ForEach-Object {
        $key, $value = $_ -split '=', 2
        $props[$key.Trim()] = $value.Trim()
    }

    $appId   = $props['firebase.appId']
    $testers = $props['firebase.testers']

    if ([string]::IsNullOrEmpty($appId) -or $appId -eq 'PASTE_APP_ID_HERE') {
        throw "firebase.appId is not set in local.properties. Paste the App ID from the Firebase Console."
    }
    if ([string]::IsNullOrEmpty($testers)) {
        throw "firebase.testers is not set in local.properties."
    }

    # ── Release notes ──────────────────────────────────────────────────────────
    $notesFile = Join-Path $ProjectRoot "app\release-notes.txt"
    if (-not (Test-Path $notesFile)) {
        throw "app\release-notes.txt not found. Create it with a short description of this build."
    }
    $notes = (Get-Content $notesFile -Raw).Trim()
    if ([string]::IsNullOrEmpty($notes)) {
        throw "app\release-notes.txt is empty. Add a short description before distributing."
    }

    # ── Summary before building ────────────────────────────────────────────────
    Write-Host ""
    Write-Host "  App ID  : $appId" -ForegroundColor Cyan
    Write-Host "  Testers : $testers" -ForegroundColor Cyan
    Write-Host "  Notes   : $notes" -ForegroundColor Cyan
    Write-Host ""

    # ── Build ──────────────────────────────────────────────────────────────────
    Write-Host "Building debug APK..." -ForegroundColor Yellow
    & ".\gradlew.bat" assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)." }

    $apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) {
        throw "APK not found at expected path: $apk"
    }

    # ── Distribute ─────────────────────────────────────────────────────────────
    Write-Host ""
    Write-Host "Uploading to Firebase App Distribution..." -ForegroundColor Yellow
    # Use --release-notes-file so multi-line notes aren't split into extra CLI args
    firebase appdistribution:distribute $apk `
        --app $appId `
        --testers $testers `
        --release-notes-file $notesFile

    if ($LASTEXITCODE -ne 0) { throw "Firebase distribution failed (exit $LASTEXITCODE)." }

    Write-Host ""
    Write-Host "Done! Elly will get a notification to install the new build." -ForegroundColor Green
    Write-Host ""

} finally {
    Pop-Location
}
