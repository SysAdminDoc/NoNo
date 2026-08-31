[CmdletBinding()]
param([switch]$Clean)

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$env:JAVA_HOME = Resolve-JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$gradleArgs = @('--no-daemon')
if ($Clean) { $gradleArgs += 'clean' }
$gradleArgs += 'assembleDebug'

Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Debug build failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Build finished without expected APK: $apk" }
# This deliberately does not write to dist\. A debug APK is debuggable and signed with the
# machine's throwaway debug key, and freezing it there is how the published checksum came to
# describe one. dist\ is written only by reproducible-release.ps1.

$manifestPath = Join-Path $root 'validation\reports\build-manifest.json'
$manifest = [ordered]@{
    apk = 'app/build/outputs/apk/debug/app-debug.apk'
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash
    size_bytes = (Get-Item -LiteralPath $apk).Length
    built_at_utc = (Get-Date).ToUniversalTime().ToString('o')
}
[System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Debug APK: $apk"
Write-Host "dist is not written by this script; use reproducible-release.ps1 for a shippable artifact."
Write-Host "Build manifest: $manifestPath"
