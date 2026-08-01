[CmdletBinding()]
param(
    [string]$PackageName = 'com.samruston.buzzkill',
    [string]$Serial,
    [string]$AdbPath,
    [string]$OutputRoot = ''
)

. (Join-Path $PSScriptRoot 'common.ps1')

if (-not $OutputRoot) { $OutputRoot = Join-Path $PSScriptRoot '..' }

try {
    if ($PackageName -notmatch '^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$') {
        throw "Invalid Android package name: $PackageName"
    }
    $adb = Resolve-AdbPath -AdbPath $AdbPath
    $device = Resolve-DeviceSerial -Adb $adb -Serial $Serial
    $root = (Resolve-Path -LiteralPath $OutputRoot).Path
    $packageDir = Join-Path $root 'evidence\package'
    New-EvidenceDirectory -Path $packageDir
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'

    $installed = Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'pm', 'list', 'packages', $PackageName)
    if (-not ($installed -match "package:$([regex]::Escape($PackageName))")) {
        throw "Package '$PackageName' is not installed on device '$device'."
    }

    $captures = @(
        @{ Name = 'dumpsys-package'; Args = @('-s', $device, 'shell', 'dumpsys', 'package', $PackageName) },
        @{ Name = 'appops'; Args = @('-s', $device, 'shell', 'appops', 'get', $PackageName) },
        @{ Name = 'apk-paths'; Args = @('-s', $device, 'shell', 'pm', 'path', $PackageName) },
        @{ Name = 'resolve-activity'; Args = @('-s', $device, 'shell', 'cmd', 'package', 'resolve-activity', '--brief', $PackageName) },
        @{ Name = 'activities'; Args = @('-s', $device, 'shell', 'dumpsys', 'activity', 'activities', $PackageName) },
        @{ Name = 'jobscheduler'; Args = @('-s', $device, 'shell', 'dumpsys', 'jobscheduler', $PackageName) }
    )

    foreach ($capture in $captures) {
        $path = Get-UniqueEvidencePath -Directory $packageDir -BaseName ("$($capture.Name)-$timestamp") -Extension '.txt'
        Save-AdbTextOutput -Adb $adb -Arguments $capture.Args -OutputPath $path
        Write-Host "$($capture.Name): $path"
    }
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
