[CmdletBinding()]
param(
    [string]$Serial = '',
    [string[]]$ScreenId = @()
)

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$target = Get-TargetSerial -Serial $Serial
$matrixPath = Join-Path $root 'validation\screen-validation-matrix.csv'
if (-not (Test-Path -LiteralPath $matrixPath -PathType Leaf)) { throw "Visual validation matrix not found: $matrixPath" }
$rows = @(Import-Csv -LiteralPath $matrixPath | Where-Object { $_.enabled -eq 'true' })
if ($ScreenId.Count -gt 0) { $rows = @($rows | Where-Object { $ScreenId -contains $_.screen_id }) }
if ($rows.Count -eq 0) { throw 'No enabled visual validation rows matched the request.' }

$results = New-Object System.Collections.Generic.List[object]
foreach ($row in $rows) {
    Write-Host "Validating $($row.screen_id)..."
    $baseline = Join-Path $root $row.baseline
    $current = Join-Path $root "validation\current\$($row.screen_id).png"
    $mask = if ([string]::IsNullOrWhiteSpace($row.mask)) { '' } else { Join-Path $root $row.mask }
    $status = 'ERROR'
    $similarity = ''
    $detail = ''
    try {
        & (Join-Path $PSScriptRoot 'capture-replica-screen.ps1') -Serial $target -ScreenId $row.screen_id -OutFile $current -Force | Out-Null
        & (Join-Path $PSScriptRoot 'compare-screen.ps1') -ScreenId $row.screen_id -Baseline $baseline -Current $current -Threshold ([double]$row.threshold) -Mask $mask | Out-Null
        $status = 'PASS'
    } catch {
        $status = 'FAIL'
        $detail = $_.Exception.Message
    }
    $metricsPath = Join-Path $root "validation\diffs\$($row.screen_id)-metrics.json"
    if (Test-Path -LiteralPath $metricsPath) {
        $metrics = Get-Content -LiteralPath $metricsPath -Raw | ConvertFrom-Json
        $similarity = $metrics.pixel_similarity
        if ($metrics.result -ne 'PASS') { $status = 'FAIL' }
    }
    $results.Add([pscustomobject][ordered]@{
        screen_id = $row.screen_id
        surface_group = $row.surface_group
        threshold = $row.threshold
        pixel_similarity = $similarity
        status = $status
        detail = $detail
        baseline = $row.baseline
        current = "validation/current/$($row.screen_id).png"
        metrics = "validation/diffs/$($row.screen_id)-metrics.json"
    })
}

$reportCsv = Join-Path $root 'validation\reports\visual-validation-last-run.csv'
$results | Export-Csv -LiteralPath $reportCsv -NoTypeInformation -Encoding UTF8
$passed = @($results | Where-Object { $_.status -eq 'PASS' }).Count
$failed = $results.Count - $passed
$reportMd = Join-Path $root 'validation\reports\visual-validation-last-run.md'
$lines = @(
    '# Visual validation report',
    '',
    "- Device: $target",
    "- Compared: $($results.Count)",
    "- Passed configured threshold: $passed",
    "- Below threshold or failed: $failed",
    '- Matrix: validation/screen-validation-matrix.csv',
    '- Detailed metrics/artifacts: validation/diffs',
    '',
    '| Screen | Group | Similarity | Threshold | Result |',
    '|---|---|---:|---:|---|'
)
foreach ($result in $results) { $lines += "| $($result.screen_id) | $($result.surface_group) | $($result.pixel_similarity) | $($result.threshold) | $($result.status) |" }
$lines | Set-Content -LiteralPath $reportMd -Encoding UTF8
& (Join-Path $PSScriptRoot 'aggregate-visual-report.ps1')
Write-Host "Visual validation complete: $passed passed, $failed failed. Report: $reportMd"
if ($failed -gt 0) { throw "$failed visual validation rows did not meet their configured threshold." }
