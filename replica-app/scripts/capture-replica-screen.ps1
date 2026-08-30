[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ScreenId,
    [string]$Serial = '',
    [string]$OutFile = '',
    [switch]$Force
)

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$target = Get-TargetSerial -Serial $Serial
if ([string]::IsNullOrWhiteSpace($OutFile)) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutFile = Join-Path $root "validation\current\$ScreenId-$stamp.png"
}
$OutFile = [System.IO.Path]::GetFullPath($OutFile)
Ensure-Directory (Split-Path -Parent $OutFile)
if ((Test-Path -LiteralPath $OutFile) -and -not $Force) { throw "Refusing to overwrite capture: $OutFile" }

$landscape = $ScreenId -eq '087_rules_landscape_native'
$previousRotationMode = ''
$previousAccelerometerRotation = ''
if ($landscape) {
    $adb = Resolve-AdbPath
    $previousRotationMode = (& $adb -s $target shell wm user-rotation 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the current user-rotation mode.' }
    $previousAccelerometerRotation = (& $adb -s $target shell settings get system accelerometer_rotation 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not read the current accelerometer-rotation setting.' }
    Invoke-Adb -Serial $target -Arguments @('shell', 'wm', 'user-rotation', 'lock', '1')
    Start-Sleep -Milliseconds 800
}
$captured = $false
$remote = "/sdcard/nono-$ScreenId.png"
$remoteUi = "/sdcard/nono-$ScreenId-capture.xml"
$requiresKeyboard = $ScreenId -match '^(014|015|031|041|046|047|048|068|069)_'
try {
    & (Join-Path $PSScriptRoot 'launch-replica.ps1') -Serial $target -State $ScreenId
    if ($LASTEXITCODE -ne 0) { throw "Could not launch state $ScreenId." }
    if ($requiresKeyboard) {
        Start-Sleep -Milliseconds 700
        Invoke-Adb -Serial $target -Arguments @('shell', 'uiautomator', 'dump', '--compressed', $remoteUi) | Out-Null
        $xmlText = (Invoke-Adb -Serial $target -Arguments @('shell', 'cat', $remoteUi) | Out-String)
        $editBounds = [regex]::Match($xmlText, 'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if ($editBounds.Success) {
            $tapX = [int](0.5 * ([int]$editBounds.Groups[1].Value + [int]$editBounds.Groups[3].Value))
            $tapY = [int](0.5 * ([int]$editBounds.Groups[2].Value + [int]$editBounds.Groups[4].Value))
            Invoke-Adb -Serial $target -Arguments @('shell', 'input', 'tap', $tapX.ToString(), $tapY.ToString()) | Out-Null
        }
    }
    Start-Sleep -Milliseconds 1800
    Invoke-Adb -Serial $target -Arguments @('shell', 'screencap', '-p', $remote)
    Invoke-Adb -Serial $target -Arguments @('pull', $remote, $OutFile)
    $captured = $true
} finally {
    try { Invoke-Adb -Serial $target -Arguments @('shell', 'rm', $remote) } catch { Write-Warning $_.Exception.Message }
    if ($requiresKeyboard) {
        try { Invoke-Adb -Serial $target -Arguments @('shell', 'rm', $remoteUi) } catch { Write-Warning $_.Exception.Message }
    }
    if ($landscape) {
        try {
            if ($previousRotationMode -match '^lock\s+([0-3])$') {
                Invoke-Adb -Serial $target -Arguments @('shell', 'wm', 'user-rotation', 'lock', $Matches[1])
            } else {
                Invoke-Adb -Serial $target -Arguments @('shell', 'wm', 'user-rotation', 'free')
            }
            if ($previousAccelerometerRotation -match '^[01]$') {
                Invoke-Adb -Serial $target -Arguments @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', $previousAccelerometerRotation)
            }
        } catch { Write-Warning "Could not restore the prior rotation state: $($_.Exception.Message)" }
    }
}
if (-not $captured -or -not (Test-Path -LiteralPath $OutFile -PathType Leaf)) { throw "Screenshot was not created: $OutFile" }
Write-Host "Captured $ScreenId to $OutFile"
