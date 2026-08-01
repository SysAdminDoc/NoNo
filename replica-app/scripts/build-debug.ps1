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
Write-Host "Debug APK: $apk"
