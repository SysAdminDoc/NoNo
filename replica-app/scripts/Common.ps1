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

# --- Machine-readable test evidence -----------------------------------------
# Traceability status must be derived from recorded results, never asserted.
# Runner scripts convert JUnit XML into a durable summary under
# validation\reports; finalize-documentation.ps1 reads only those summaries.

function Get-JUnitSummary {
    param(
        [Parameter(Mandatory = $true)][string]$ResultsDirectory,
        [Parameter(Mandatory = $true)][string]$Suite
    )
    $summary = [ordered]@{
        suite         = $Suite
        source        = $ResultsDirectory
        generated_at  = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ssK')
        tests         = 0
        failures      = 0
        errors        = 0
        skipped       = 0
        suite_files   = 0
        status        = 'NOT_RUN'
    }
    if (-not (Test-Path -LiteralPath $ResultsDirectory)) { return $summary }
    $files = @(Get-ChildItem -LiteralPath $ResultsDirectory -Filter 'TEST-*.xml' -Recurse -ErrorAction SilentlyContinue)
    if ($files.Count -eq 0) { return $summary }
    foreach ($file in $files) {
        $xml = [xml](Get-Content -LiteralPath $file.FullName -Raw)
        foreach ($node in @($xml.SelectNodes('//testsuite'))) {
            $summary.tests += [int]$node.tests
            $summary.failures += [int]$node.failures
            $summary.errors += [int]$node.errors
            if ($node.HasAttribute('skipped')) { $summary.skipped += [int]$node.skipped }
        }
    }
    $summary.suite_files = $files.Count
    if ($summary.tests -eq 0) { $summary.status = 'NOT_RUN' }
    elseif (($summary.failures + $summary.errors) -gt 0) { $summary.status = 'FAIL' }
    else { $summary.status = 'PASS' }
    return $summary
}

function Save-TestSummary {
    param(
        [Parameter(Mandatory = $true)]$Summary,
        [Parameter(Mandatory = $true)][string]$Path
    )
    Ensure-Directory (Split-Path -Parent $Path)
    $json = $Summary | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($Path, $json, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Recorded $($Summary.suite) evidence: $($Summary.status) ($($Summary.tests) tests) -> $Path"
}

function Get-TestSummaryStatus {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return 'NOT_RUN' }
    try {
        $summary = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        return 'NOT_RUN'
    }
    if ($null -eq $summary -or -not ($summary.PSObject.Properties.Name -contains 'status')) { return 'NOT_RUN' }
    if ([string]::IsNullOrWhiteSpace([string]$summary.status)) { return 'NOT_RUN' }
    return [string]$summary.status
}
