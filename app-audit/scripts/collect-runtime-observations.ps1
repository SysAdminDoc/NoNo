[CmdletBinding()]
param(
    [string]$Serial,
    [string]$AdbPath,
    [string]$OutputRoot = '',
    [string]$PackageName = 'com.samruston.buzzkill',
    [string]$LaunchComponent = 'com.samruston.buzzkill/.ui.MainActivity'
)

. (Join-Path $PSScriptRoot 'common.ps1')

if (-not $OutputRoot) { $OutputRoot = Join-Path $PSScriptRoot '..' }

try {
    $adb = Resolve-AdbPath -AdbPath $AdbPath
    $device = Resolve-DeviceSerial -Adb $adb -Serial $Serial
    $root = (Resolve-Path -LiteralPath $OutputRoot).Path
    $directory = Join-Path $root 'evidence\measurements'
    New-EvidenceDirectory -Path $directory
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'meminfo', $PackageName) -OutputPath (Get-UniqueEvidencePath -Directory $directory -BaseName "runtime-meminfo-before-$timestamp" -Extension '.txt')
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'gfxinfo', $PackageName) -OutputPath (Get-UniqueEvidencePath -Directory $directory -BaseName "runtime-gfxinfo-before-$timestamp" -Extension '.txt')

    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
    Start-Sleep -Milliseconds 500
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'am', 'start', '-W', '-n', $LaunchComponent) -OutputPath (Get-UniqueEvidencePath -Directory $directory -BaseName "runtime-warm-start-$timestamp" -Extension '.txt')
    Start-Sleep -Milliseconds 750

    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'am', 'force-stop', $PackageName) | Out-Null
    Start-Sleep -Milliseconds 500
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'am', 'start', '-W', '-n', $LaunchComponent) -OutputPath (Get-UniqueEvidencePath -Directory $directory -BaseName "runtime-cold-start-$timestamp" -Extension '.txt')
    Start-Sleep -Milliseconds 1000

    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'pidof', $PackageName) -OutputPath (Get-UniqueEvidencePath -Directory $directory -BaseName "runtime-pid-after-cold-start-$timestamp" -Extension '.txt')
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'meminfo', $PackageName) -OutputPath (Get-UniqueEvidencePath -Directory $directory -BaseName "runtime-meminfo-after-cold-start-$timestamp" -Extension '.txt')
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'gfxinfo', $PackageName) -OutputPath (Get-UniqueEvidencePath -Directory $directory -BaseName "runtime-gfxinfo-after-cold-start-$timestamp" -Extension '.txt')

    Write-Host "Collected lifecycle and performance observations for $PackageName on $device"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
