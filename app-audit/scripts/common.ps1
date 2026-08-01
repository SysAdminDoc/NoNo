Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

function Resolve-AdbPath {
    param([string]$AdbPath)

    if ($AdbPath) {
        if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
            throw "ADB was not found at the supplied path: $AdbPath"
        }
        return (Resolve-Path -LiteralPath $AdbPath).Path
    }

    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $fallback = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $fallback -PathType Leaf) { return $fallback }

    throw 'ADB was not found. Supply -AdbPath or install Android SDK Platform-Tools.'
}

function Invoke-AdbText {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    foreach ($argument in $Arguments) {
        if ($argument -match '[\s"]') {
            throw "ADB received an unsupported argument containing whitespace or quotes: $argument"
        }
    }

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Adb
    $startInfo.Arguments = $Arguments -join ' '
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw 'Failed to start ADB.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    [System.Threading.Tasks.Task]::WaitAll(@($stdoutTask, $stderrTask))
    $stdout = $stdoutTask.Result
    $stderr = $stderrTask.Result
    if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "ADB failed with exit code $($process.ExitCode): $($Arguments -join ' ')`n$stderr"
    }
    if (-not $stdout) { return @() }
    return ,@($stdout.TrimEnd("`r", "`n") -split "`r?`n")
}

function Resolve-DeviceSerial {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [string]$Serial
    )

    $lines = Invoke-AdbText -Adb $Adb -Arguments @('devices', '-l')
    $devices = @()
    foreach ($line in $lines) {
        if ($line -match '^([^\s]+)\s+(device|unauthorized|offline)\b') {
            $devices += [pscustomobject]@{ Serial = $matches[1]; State = $matches[2]; Detail = $line }
        }
    }

    if ($Serial) {
        $selected = @($devices | Where-Object { $_.Serial -eq $Serial })
        if ($selected.Count -ne 1) { throw "Device '$Serial' was not found in adb devices -l." }
        if ($selected[0].State -ne 'device') { throw "Device '$Serial' is $($selected[0].State), not authorized and ready." }
        return $Serial
    }

    $ready = @($devices | Where-Object { $_.State -eq 'device' })
    if ($ready.Count -ne 1) {
        $summary = if ($devices.Count) { ($devices.Detail -join [Environment]::NewLine) } else { '(none)' }
        throw "Expected exactly one authorized device. Found $($ready.Count). Devices:`n$summary"
    }
    return $ready[0].Serial
}

function New-EvidenceDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Get-UniqueEvidencePath {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$BaseName,
        [Parameter(Mandatory = $true)][string]$Extension
    )

    New-EvidenceDirectory -Path $Directory
    $candidate = Join-Path $Directory ($BaseName + $Extension)
    if (-not (Test-Path -LiteralPath $candidate)) { return $candidate }

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $candidate = Join-Path $Directory ($BaseName + '-' + $timestamp + $Extension)
    if (Test-Path -LiteralPath $candidate) {
        throw "Refusing to overwrite existing evidence: $candidate"
    }
    return $candidate
}

function Save-AdbBinaryOutput {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    if (Test-Path -LiteralPath $OutputPath) { throw "Refusing to overwrite: $OutputPath" }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Adb
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    foreach ($argument in $Arguments) {
        if ($argument -match '[\s"]') {
            throw "Binary ADB capture received an unsupported argument containing whitespace or quotes: $argument"
        }
    }
    $startInfo.Arguments = $Arguments -join ' '

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    if (-not $process.Start()) { throw 'Failed to start ADB.' }
    $file = [System.IO.File]::Open($OutputPath, [System.IO.FileMode]::CreateNew)
    try {
        $process.StandardOutput.BaseStream.CopyTo($file)
    } finally {
        $file.Dispose()
    }
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        Remove-Item -LiteralPath $OutputPath -Force -ErrorAction SilentlyContinue
        throw "ADB binary capture failed with exit code $($process.ExitCode): $stderr"
    }
}

function Save-AdbTextOutput {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    if (Test-Path -LiteralPath $OutputPath) { throw "Refusing to overwrite: $OutputPath" }
    $output = Invoke-AdbText -Adb $Adb -Arguments $Arguments
    [System.IO.File]::WriteAllText($OutputPath, ($output -join [Environment]::NewLine), (New-Object System.Text.UTF8Encoding($false)))
}
