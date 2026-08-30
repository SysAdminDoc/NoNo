[CmdletBinding()]
param(
    [string]$Serial = '',
    [switch]$Force
)

. (Join-Path $PSScriptRoot 'Common.ps1')
if (-not $Force) { throw 'Reset clears only the replica package data. Pass -Force to confirm.' }
$target = Get-TargetSerial -Serial $Serial
$package = 'com.sysadmindoc.nono.debug'
Invoke-Adb -Serial $target -Arguments @('shell', 'pm', 'clear', $package)
Write-Host "Cleared local test data for $package on $target. The original audited app was not changed."
