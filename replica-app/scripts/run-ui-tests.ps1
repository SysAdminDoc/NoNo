[CmdletBinding()]
param([string]$Serial = '')

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$target = Get-TargetSerial -Serial $Serial
$env:ANDROID_SERIAL = $target
$env:JAVA_HOME = Resolve-JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') --no-daemon connectedDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "Instrumented UI tests failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
Write-Host "UI tests passed on $target. Report: $(Join-Path $root 'app\build\reports\androidTests\connected\debug\index.html')"
