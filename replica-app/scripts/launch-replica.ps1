[CmdletBinding()]
param(
    [string]$Serial = '',
    [string]$State = '',
    [switch]$Warm
)

. (Join-Path $PSScriptRoot 'Common.ps1')
$target = Get-TargetSerial -Serial $Serial
$package = 'com.anm.signalrules.reconstruction.debug'
$component = 'com.anm.signalrules.reconstruction.debug/com.anm.signalrules.reconstruction.MainActivity'
if (-not $Warm) { Invoke-Adb -Serial $target -Arguments @('shell', 'am', 'force-stop', $package) }
$args = @('shell', 'am', 'start', '-W', '-n', $component)
if (-not [string]::IsNullOrWhiteSpace($State)) { $args += @('--es', 'replica_state', $State) }
Invoke-Adb -Serial $target -Arguments $args
Write-Host "Launched Signal Rules on $target$(if ($State) { " in state $State" } else { '' })."
