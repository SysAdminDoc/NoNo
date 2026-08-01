[CmdletBinding()]
param(
    [string]$Serial,
    [string]$AdbPath,
    [string]$OutputRoot = ''
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

    $captures = [ordered]@{
        'adb-devices' = @('devices', '-l')
        'manufacturer' = @('-s', $device, 'shell', 'getprop', 'ro.product.manufacturer')
        'model' = @('-s', $device, 'shell', 'getprop', 'ro.product.model')
        'device' = @('-s', $device, 'shell', 'getprop', 'ro.product.device')
        'android-release' = @('-s', $device, 'shell', 'getprop', 'ro.build.version.release')
        'android-sdk' = @('-s', $device, 'shell', 'getprop', 'ro.build.version.sdk')
        'build-fingerprint' = @('-s', $device, 'shell', 'getprop', 'ro.build.fingerprint')
        'build-display-id' = @('-s', $device, 'shell', 'getprop', 'ro.build.display.id')
        'wm-size' = @('-s', $device, 'shell', 'wm', 'size')
        'wm-density' = @('-s', $device, 'shell', 'wm', 'density')
        'font-scale' = @('-s', $device, 'shell', 'settings', 'get', 'system', 'font_scale')
        'locale' = @('-s', $device, 'shell', 'getprop', 'persist.sys.locale')
        'orientation' = @('-s', $device, 'shell', 'settings', 'get', 'system', 'user_rotation')
        'accelerometer-rotation' = @('-s', $device, 'shell', 'settings', 'get', 'system', 'accelerometer_rotation')
        'navigation-mode' = @('-s', $device, 'shell', 'settings', 'get', 'secure', 'navigation_mode')
        'ui-night-mode' = @('-s', $device, 'shell', 'cmd', 'uimode', 'night')
        'enabled-accessibility-services' = @('-s', $device, 'shell', 'settings', 'get', 'secure', 'enabled_accessibility_services')
        'display' = @('-s', $device, 'shell', 'dumpsys', 'display')
        'window-displays' = @('-s', $device, 'shell', 'dumpsys', 'window', 'displays')
        'input-method' = @('-s', $device, 'shell', 'dumpsys', 'input_method')
    }

    foreach ($entry in $captures.GetEnumerator()) {
        $path = Get-UniqueEvidencePath -Directory $directory -BaseName ("device-$($entry.Key)-$timestamp") -Extension '.txt'
        Save-AdbTextOutput -Adb $adb -Arguments $entry.Value -OutputPath $path
    }

    Write-Host "Collected device baseline for $device in $directory"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
