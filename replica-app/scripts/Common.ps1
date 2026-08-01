Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

function Get-ProjectRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function Resolve-AdbPath {
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
    $candidates += 'D:\tools\android-sdk\platform-tools\adb.exe'
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { $candidates += $command.Source }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return [System.IO.Path]::GetFullPath($candidate) }
    }
    throw 'ADB was not found. Set ANDROID_SDK_ROOT or install Android platform-tools.'
}

function Resolve-JavaHome {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME) -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return $env:JAVA_HOME }
    if (Test-Path -LiteralPath 'D:\tools\jdk21\bin\java.exe') { return 'D:\tools\jdk21' }
    throw 'A compatible JDK was not found. Set JAVA_HOME to JDK 17 or newer.'
}

function Get-TargetSerial {
    param([string]$Serial = '')
    $adb = Resolve-AdbPath
    $lines = & $adb devices
    if ($LASTEXITCODE -ne 0) { throw 'adb devices failed.' }
    $devices = @($lines | Select-String -Pattern '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if (-not [string]::IsNullOrWhiteSpace($Serial)) {
        if ($devices -notcontains $Serial) { throw "Requested device '$Serial' is not connected and authorized. Available: $($devices -join ', ')" }
        return $Serial
    }
    if ($devices.Count -ne 1) { throw "Exactly one device is required when -Serial is omitted. Connected authorized devices: $($devices -join ', ')" }
    return $devices[0]
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    $adb = Resolve-AdbPath
    & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -eq 0) { return }
    Start-Sleep -Milliseconds 500
    & $adb start-server | Out-Null
    & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed for '$Serial' after retry: $($Arguments -join ' ')" }
}

function Ensure-Directory {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { New-Item -ItemType Directory -Path $Path -Force | Out-Null }
}
