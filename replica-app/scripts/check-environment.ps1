[CmdletBinding()]
param([string]$Serial = '')

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$adb = Resolve-AdbPath
$javaHome = Resolve-JavaHome
$target = Get-TargetSerial -Serial $Serial
$wrapper = Join-Path $root 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw "Gradle wrapper not found: $wrapper" }

Write-Host "Project: $root"
Write-Host "ADB: $adb"
Write-Host "JDK: $javaHome"
Write-Host "Device: $target"
Invoke-Adb -Serial $target -Arguments @('shell', 'getprop', 'ro.build.version.release')
Invoke-Adb -Serial $target -Arguments @('shell', 'wm', 'size')
Invoke-Adb -Serial $target -Arguments @('shell', 'wm', 'density')
Write-Host 'Environment check passed.'
