[CmdletBinding()]
param([string]$Serial = '')

. (Join-Path $PSScriptRoot 'Common.ps1')
$target = Get-TargetSerial -Serial $Serial
$apk = Join-Path (Get-ProjectRoot) 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "Debug APK not found. Run build-debug.ps1 first: $apk" }
Invoke-Adb -Serial $target -Arguments @('install', '-r', $apk)
Write-Host "Installed NoNo debug APK on $target."
