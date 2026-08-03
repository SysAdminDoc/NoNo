[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$matrixPath = Join-Path $root 'validation\screen-validation-matrix.csv'
$rows = @(Import-Csv -LiteralPath $matrixPath | Where-Object { $_.enabled -eq 'true' })
$lastRunPath = Join-Path $root 'validation\reports\visual-validation-last-run.csv'
$lastRun = @{}
if (Test-Path -LiteralPath $lastRunPath -PathType Leaf) {
    foreach ($item in @(Import-Csv -LiteralPath $lastRunPath)) { $lastRun[$item.screen_id] = $item }
}
$results = New-Object System.Collections.Generic.List[object]

foreach ($row in $rows) {
    $metricsPath = Join-Path $root "validation\diffs\$($row.screen_id)-metrics.json"
    $status = 'MISSING'
    $similarity = ''
    $detail = ''
    if ($lastRun.ContainsKey($row.screen_id)) {
        $run = $lastRun[$row.screen_id]
        $status = [string]$run.status
        $similarity = [string]$run.pixel_similarity
        $detail = [string]$run.detail
    } elseif (Test-Path -LiteralPath $metricsPath -PathType Leaf) {
        try {
            $metrics = Get-Content -LiteralPath $metricsPath -Raw | ConvertFrom-Json
            $similarity = $metrics.pixel_similarity
            $status = if ($metrics.result -eq 'PASS') { 'PASS' } else { 'FAIL' }
        } catch {
            $status = 'ERROR'
            $detail = $_.Exception.Message
        }
    } else {
        $detail = 'Metrics artifact was not found.'
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

$reportDirectory = Join-Path $root 'validation\reports'
Ensure-Directory $reportDirectory
$reportCsv = Join-Path $reportDirectory 'visual-validation-results.csv'
$results | Export-Csv -LiteralPath $reportCsv -NoTypeInformation -Encoding UTF8
$passed = @($results | Where-Object { $_.status -eq 'PASS' }).Count
$thresholdMisses = @($results | Where-Object { $_.status -in @('FAIL', 'THRESHOLD_MISS') }).Count
$operationalFailures = @($results | Where-Object { $_.status -in @('CAPTURE_ERROR', 'COMPARE_ERROR', 'ERROR') }).Count
$missing = $results.Count - $passed - $thresholdMisses - $operationalFailures
$reportMd = Join-Path $reportDirectory 'visual-validation-report.md'
$lines = @(
    '# Visual validation report',
    '',
    "- Compared: $($results.Count)",
    "- Passed configured threshold: $passed",
    "- Below threshold: $thresholdMisses",
    "- Operational failures: $operationalFailures",
    "- Missing or invalid result: $missing",
    '- Matrix: validation/screen-validation-matrix.csv',
    '- Detailed metrics and comparison images: validation/diffs',
    '',
    '| Screen | Group | Similarity | Threshold | Result |',
    '|---|---|---:|---:|---|'
)
foreach ($result in $results) {
    $lines += "| $($result.screen_id) | $($result.surface_group) | $($result.pixel_similarity) | $($result.threshold) | $($result.status) |"
}
$lines | Set-Content -LiteralPath $reportMd -Encoding UTF8
Write-Host "Aggregated visual validation: $passed passed, $thresholdMisses threshold misses, $operationalFailures operational failures, $missing missing. Report: $reportMd"
if ($missing -gt 0) { throw "$missing visual validation rows have no valid metrics artifact." }
