[CmdletBinding()]
param(
    [string]$Serial = '',
    [switch]$AllowMismatch
)

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$adb = Resolve-AdbPath
$javaHome = Resolve-JavaHome
$target = Get-TargetSerial -Serial $Serial
$wrapper = Join-Path $root 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw "Gradle wrapper not found: $wrapper" }

# Reference device recorded in app-audit/device/device-environment.json. Baseline captures
# are only comparable against a device that matches it, so a mismatch is an error rather
# than a line of output the operator is expected to notice.
$expected = [ordered]@{
    'API level'  = @{ Command = @('shell', 'getprop', 'ro.build.version.sdk'); Expected = '36' }
    'Resolution' = @{ Command = @('shell', 'wm', 'size'); Expected = 'Physical size: 1080x2400' }
    'Density'    = @{ Command = @('shell', 'wm', 'density'); Expected = 'Physical density: 420' }
    'Locale'     = @{ Command = @('shell', 'getprop', 'persist.sys.locale'); Expected = 'en-US'; Fallback = @('shell', 'getprop', 'ro.product.locale') }
    'Font scale' = @{ Command = @('shell', 'settings', 'get', 'system', 'font_scale'); Expected = '1.0'; AllowNull = $true }
}

Write-Host "Project: $root"
Write-Host "ADB: $adb"
Write-Host "JDK: $javaHome"
Write-Host "Device: $target"

$mismatches = @()
foreach ($name in $expected.Keys) {
    $spec = $expected[$name]
    $actual = (Invoke-Adb -Serial $target -Arguments $spec.Command | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($actual) -and $spec.Contains('Fallback')) {
        $actual = (Invoke-Adb -Serial $target -Arguments $spec.Fallback | Out-String).Trim()
    }
    # font_scale is unset on a freshly created AVD and defaults to 1.0.
    if ([string]::IsNullOrWhiteSpace($actual) -or $actual -eq 'null') {
        if ($spec.Contains('AllowNull') -and $spec.AllowNull) { $actual = $spec.Expected }
    }
    $ok = $actual -eq $spec.Expected
    Write-Host ("{0,-11} {1,-30} {2}" -f $name, $actual, $(if ($ok) { 'OK' } else { "EXPECTED $($spec.Expected)" }))
    if (-not $ok) { $mismatches += "$name is '$actual', expected '$($spec.Expected)'" }
}

if ($mismatches.Count -gt 0) {
    $detail = $mismatches -join '; '
    if ($AllowMismatch) {
        Write-Host "WARNING: device does not match the reference environment: $detail"
        Write-Host 'Visual comparison results from this device are not valid against the audit baselines.'
        return
    }
    throw "Device does not match the reference environment: $detail. Re-run with -AllowMismatch only if you are not comparing against audit baselines."
}
Write-Host 'Environment check passed.'
