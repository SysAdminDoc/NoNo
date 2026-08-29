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

$script:SupportedJavaMajors = @(21)

function Get-JavaMajor {
    param([Parameter(Mandatory = $true)][string]$JavaHome)
    $exe = Join-Path $JavaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { return $null }
    # java -version writes its banner to stderr. Under Windows PowerShell 5.1 a redirected native
    # stderr line becomes an ErrorRecord, which $ErrorActionPreference = 'Stop' turns into a
    # terminating error, so this must not use 2>&1. The release file avoids the problem entirely.
    $releaseFile = Join-Path $JavaHome 'release'
    if (Test-Path -LiteralPath $releaseFile -PathType Leaf) {
        $line = Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="([^"]+)"' |
            Select-Object -First 1
        if ($null -ne $line) {
            $version = $line.Matches[0].Groups[1].Value
            if ($version -match '^1\.(\d+)') { return [int]$Matches[1] }
            if ($version -match '^(\d+)') { return [int]$Matches[1] }
        }
    }
    # Fall back to asking the binary, capturing stderr through a file rather than the pipeline.
    $stderrPath = [System.IO.Path]::GetTempFileName()
    try {
        Start-Process -FilePath $exe -ArgumentList '-version' -NoNewWindow -Wait `
            -RedirectStandardError $stderrPath -RedirectStandardOutput ([System.IO.Path]::GetTempFileName()) | Out-Null
        $output = Get-Content -LiteralPath $stderrPath -Raw
        if ($output -match 'version "(\d+)') { return [int]$Matches[1] }
        if ($output -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    } catch {
        return $null
    } finally {
        Remove-Item -LiteralPath $stderrPath -ErrorAction SilentlyContinue
    }
    return $null
}

# The build rejects unsupported JDKs in settings.gradle, so a script that hands Gradle the
# Android Studio JBR (OpenJDK 25) only moves the failure later. Pick a supported one here.
function Resolve-JavaHome {
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $candidates += $env:JAVA_HOME }
    $candidates += 'D:\tools\jdk21'
    foreach ($root in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path -LiteralPath $root) {
            $candidates += (Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
        }
    }

    $rejected = @()
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $major = Get-JavaMajor -JavaHome $candidate
        if ($null -eq $major) { continue }
        if ($script:SupportedJavaMajors -contains $major) { return [System.IO.Path]::GetFullPath($candidate) }
        $rejected += "$candidate (JDK $major)"
    }

    $detail = if ($rejected.Count -gt 0) { " Rejected: $($rejected -join '; ')." } else { '' }
    throw "No supported JDK was found. This build needs JDK $($script:SupportedJavaMajors -join ' or ').$detail Set JAVA_HOME to a supported JDK."
}

# Prefers the Windows launcher, which is how Python is invoked on this workstation;
# a bare python.exe is often absent or is a Store alias stub.
function Resolve-Python {
    $launcher = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($null -ne $launcher) { return $launcher.Source }
    $direct = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($null -ne $direct) { return $direct.Source }
    throw 'Python 3 was not found. Install it, or add the py launcher to PATH.'
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
