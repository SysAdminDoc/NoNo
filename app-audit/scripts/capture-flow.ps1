[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9_]+$')]
    [string]$FlowId,
    [string]$Serial,
    [string]$AdbPath,
    [string]$OutputRoot = '',
    [ValidateRange(1, 60)]
    [int]$DurationSeconds = 30,
    [ValidateRange(1000000, 20000000)]
    [int]$BitRate = 8000000,
    [string]$Notes = ''
)

. (Join-Path $PSScriptRoot 'common.ps1')

if (-not $OutputRoot) { $OutputRoot = Join-Path $PSScriptRoot '..' }

try {
    $adb = Resolve-AdbPath -AdbPath $AdbPath
    $device = Resolve-DeviceSerial -Adb $adb -Serial $Serial
    $root = (Resolve-Path -LiteralPath $OutputRoot).Path
    $recordingDir = Join-Path $root 'evidence\recordings'
    $manifestPath = Join-Path $root 'evidence\evidence-manifest.csv'
    New-EvidenceDirectory -Path $recordingDir

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $baseName = "$FlowId-$timestamp"
    $localPath = Get-UniqueEvidencePath -Directory $recordingDir -BaseName $baseName -Extension '.mp4'
    $remotePath = "/sdcard/$baseName.mp4"

    Write-Host "Recording '$FlowId' for $DurationSeconds seconds on $device..."
    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'screenrecord', '--bit-rate', "$BitRate", '--time-limit', "$DurationSeconds", $remotePath) | Out-Null
    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'pull', $remotePath, $localPath) | Out-Null
    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'rm', $remotePath) -AllowFailure | Out-Null

    $row = [pscustomobject]@{
        captured_at = (Get-Date).ToString('o')
        screen_id = ''
        evidence_type = 'flow-recording'
        screenshot = ''
        ui_xml = ''
        activity_dump = ''
        recording = $localPath.Substring($root.Length + 1)
        notes = $Notes
    }
    if (Test-Path -LiteralPath $manifestPath) {
        $row | Export-Csv -LiteralPath $manifestPath -NoTypeInformation -Append
    } else {
        $row | Export-Csv -LiteralPath $manifestPath -NoTypeInformation
    }

    Write-Host "Recording: $localPath"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
