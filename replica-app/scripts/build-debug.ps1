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
# One build, one artifact. Leaving older APKs beside the current one makes it impossible to tell
# from the directory which binary SHA256SUMS.txt describes.
$metadataPath = Join-Path $root 'app\build\outputs\apk\debug\output-metadata.json'
$versionName = (Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json).elements[0].versionName
if ([string]::IsNullOrWhiteSpace($versionName)) { throw "Could not read versionName from $metadataPath" }
$dist = Join-Path $root 'dist'
if (-not (Test-Path -LiteralPath $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }
Get-ChildItem -LiteralPath $dist -Filter '*.apk' | Remove-Item -Force
Get-ChildItem -LiteralPath $dist -Filter 'reproducibility-*.json' | Remove-Item -Force
$distApkName = "NoNo-v$versionName.apk"
$distApk = Join-Path $dist $distApkName
Copy-Item -LiteralPath $apk -Destination $distApk -Force
$distHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $distApk).Hash
[System.IO.File]::WriteAllText(
    (Join-Path $dist 'SHA256SUMS.txt'),
    "$distHash  $distApkName`n",
    (New-Object System.Text.UTF8Encoding($false))
)

$manifestPath = Join-Path $root 'validation\reports\build-manifest.json'
$manifest = [ordered]@{
    apk = 'app/build/outputs/apk/debug/app-debug.apk'
    sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash
    size_bytes = (Get-Item -LiteralPath $apk).Length
    built_at_utc = (Get-Date).ToUniversalTime().ToString('o')
}
[System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Debug APK: $apk"
Write-Host "Frozen deliverable: $distApk"
Write-Host "Build manifest: $manifestPath"
