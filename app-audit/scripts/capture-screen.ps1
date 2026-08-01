[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d{3}_[a-z0-9_]+$')]
    [string]$ScreenId,
    [string]$Serial,
    [string]$AdbPath,
    [string]$OutputRoot = '',
    [string[]]$NavigationPath = @(),
    [string]$RequiredStartingState = '',
    [int]$SettleMilliseconds = 750
)

. (Join-Path $PSScriptRoot 'common.ps1')

if (-not $OutputRoot) { $OutputRoot = Join-Path $PSScriptRoot '..' }

try {
    $adb = Resolve-AdbPath -AdbPath $AdbPath
    $device = Resolve-DeviceSerial -Adb $adb -Serial $Serial
    $root = (Resolve-Path -LiteralPath $OutputRoot).Path
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $baseName = "$ScreenId-$timestamp"

    $screenshotDir = Join-Path $root 'evidence\screenshots'
    $xmlDir = Join-Path $root 'evidence\ui-xml'
    $activityDir = Join-Path $root 'evidence\activity'
    $measurementDir = Join-Path $root 'evidence\measurements'
    $manifestPath = Join-Path $root 'evidence\evidence-manifest.csv'
    New-EvidenceDirectory -Path $screenshotDir
    New-EvidenceDirectory -Path $xmlDir
    New-EvidenceDirectory -Path $activityDir
    New-EvidenceDirectory -Path $measurementDir

    if ($SettleMilliseconds -gt 0) { Start-Sleep -Milliseconds $SettleMilliseconds }

    $screenshotPath = Get-UniqueEvidencePath -Directory $screenshotDir -BaseName $baseName -Extension '.png'
    Save-AdbBinaryOutput -Adb $adb -Arguments @('-s', $device, 'exec-out', 'screencap', '-p') -OutputPath $screenshotPath

    $remoteXml = "/sdcard/window-$timestamp.xml"
    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'uiautomator', 'dump', '--compressed', $remoteXml) | Out-Null
    $xmlPath = Get-UniqueEvidencePath -Directory $xmlDir -BaseName $baseName -Extension '.xml'
    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'pull', $remoteXml, $xmlPath) | Out-Null
    Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'rm', $remoteXml) -AllowFailure | Out-Null

    $activityPath = Get-UniqueEvidencePath -Directory $activityDir -BaseName $baseName -Extension '.txt'
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'activity', 'top') -OutputPath $activityPath
    $windowPath = Get-UniqueEvidencePath -Directory $activityDir -BaseName ($baseName + '-window') -Extension '.txt'
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'window', 'windows') -OutputPath $windowPath
    $inputPath = Get-UniqueEvidencePath -Directory $activityDir -BaseName ($baseName + '-input-method') -Extension '.txt'
    Save-AdbTextOutput -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'input_method') -OutputPath $inputPath

    $focused = Invoke-AdbText -Adb $adb -Arguments @('-s', $device, 'shell', 'dumpsys', 'window')
    $focusLines = @($focused | Where-Object { $_ -match 'mCurrentFocus|mFocusedApp' })
    $metadata = [ordered]@{
        screen_id = $ScreenId
        captured_at = (Get-Date).ToString('o')
        device_serial = $device
        navigation_path = $NavigationPath
        required_starting_state = $RequiredStartingState
        focused_window = $focusLines
        evidence = [ordered]@{
            screenshot = $screenshotPath.Substring($root.Length + 1)
            ui_xml = $xmlPath.Substring($root.Length + 1)
            activity_dump = $activityPath.Substring($root.Length + 1)
            window_dump = $windowPath.Substring($root.Length + 1)
            input_method_dump = $inputPath.Substring($root.Length + 1)
        }
    }
    $metadataPath = Get-UniqueEvidencePath -Directory $measurementDir -BaseName $baseName -Extension '.json'
    $metadata | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $metadataPath -Encoding UTF8

    $row = [pscustomobject]@{
        captured_at = $metadata.captured_at
        screen_id = $ScreenId
        evidence_type = 'screen-state'
        screenshot = $metadata.evidence.screenshot
        ui_xml = $metadata.evidence.ui_xml
        activity_dump = $metadata.evidence.activity_dump
        recording = ''
        notes = ''
    }
    if (Test-Path -LiteralPath $manifestPath) {
        $row | Export-Csv -LiteralPath $manifestPath -NoTypeInformation -Append
    } else {
        $row | Export-Csv -LiteralPath $manifestPath -NoTypeInformation
    }

    Write-Host "Captured $ScreenId on $device"
    Write-Host "Screenshot: $screenshotPath"
    Write-Host "UI XML:     $xmlPath"
    Write-Host "Metadata:   $metadataPath"
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
