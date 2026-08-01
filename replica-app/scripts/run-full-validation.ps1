[CmdletBinding()]
param([string]$Serial = '')

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$target = Get-TargetSerial -Serial $Serial
$steps = @(
    @{ Name = 'environment'; Script = 'check-environment.ps1'; NeedsDevice = $true },
    @{ Name = 'unit-tests'; Script = 'run-unit-tests.ps1'; NeedsDevice = $false },
    @{ Name = 'lint'; Script = 'run-lint.ps1'; NeedsDevice = $false },
    @{ Name = 'build'; Script = 'build-debug.ps1'; NeedsDevice = $false },
    @{ Name = 'ui-tests'; Script = 'run-ui-tests.ps1'; NeedsDevice = $true },
    @{ Name = 'install'; Script = 'install-debug.ps1'; NeedsDevice = $true },
    @{ Name = 'visual-validation'; Script = 'run-visual-validation.ps1'; NeedsDevice = $true }
)
$results = New-Object System.Collections.Generic.List[object]
foreach ($step in $steps) {
    $started = Get-Date
    $status = 'PASS'
    $detail = ''
    try {
        $scriptPath = Join-Path $PSScriptRoot $step.Script
        if ($step.NeedsDevice) { & $scriptPath -Serial $target }
        else { & $scriptPath }
    }
    catch { $status = 'FAIL'; $detail = $_.Exception.Message }
    $results.Add([pscustomobject][ordered]@{ step = $step.Name; status = $status; duration_seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 2); detail = $detail })
}
$report = Join-Path $root 'validation\reports\full-validation.json'
$results | ConvertTo-Json | Set-Content -LiteralPath $report -Encoding UTF8
$logDirectory = Join-Path $root 'validation\logs'
Ensure-Directory $logDirectory
$logPath = Join-Path $logDirectory ("full-validation-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
$results | ConvertTo-Json | Set-Content -LiteralPath $logPath -Encoding UTF8
$failed = @($results | Where-Object { $_.status -eq 'FAIL' }).Count
Write-Host "Full validation finished with $failed failed step(s). Report: $report. Log: $logPath"
if ($failed -gt 0) { throw 'One or more validation steps failed. Review full-validation.json.' }
