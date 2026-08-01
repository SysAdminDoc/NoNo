[CmdletBinding()]
param(
    [string]$Serial,
    [string]$AdbPath,
    [string]$OutputRoot = '',
    [string]$PackageName = 'com.samruston.buzzkill'
)

. (Join-Path $PSScriptRoot 'common.ps1')

if (-not $OutputRoot) { $OutputRoot = Join-Path $PSScriptRoot '..' }

try {
    $adb = Resolve-AdbPath -AdbPath $AdbPath
    $device = Resolve-DeviceSerial -Adb $adb -Serial $Serial
    $root = (Resolve-Path -LiteralPath $OutputRoot).Path
    $directory = Join-Path $root 'evidence\logs'
    New-EvidenceDirectory -Path $directory
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $pidLines = Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'pidof', $PackageName)
    $targetPid = ($pidLines -join '').Trim()
    if ($targetPid -notmatch '^\d+$') { throw "No running PID was found for $PackageName." }

    $lines = Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'logcat', '-d', '--pid', $targetPid, '-v', 'threadtime')
    $sanitized = foreach ($line in $lines) {
        $value = $line -replace '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b', '[REDACTED_EMAIL]'
        $value = $value -replace '(?i)https?://\S+', '[REDACTED_URL]'
        $value = $value -replace '(?i)(token|authorization|cookie|session)[=: ]+\S+', '$1=[REDACTED]'
        $value
    }

    $path = Get-UniqueEvidencePath -Directory $directory -BaseName "target-logcat-$timestamp" -Extension '.txt'
    [System.IO.File]::WriteAllText($path, ($sanitized -join [Environment]::NewLine), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Saved sanitized PID-scoped logcat: $path"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
