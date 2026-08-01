[CmdletBinding()]
param(
    [string]$AuditRoot = '',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($AuditRoot)) {
    $AuditRoot = Join-Path $PSScriptRoot '..\..\app-audit'
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $PSScriptRoot '..\docs\audit-traceability-matrix.csv'
}
$catalogPath = Join-Path $AuditRoot 'screens\screen-catalog.csv'
if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) {
    throw "Audit screen catalog not found: $catalogPath"
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
if (Test-Path -LiteralPath $OutputPath) {
    throw "Refusing to overwrite existing traceability matrix: $OutputPath"
}

$rows = foreach ($screen in (Import-Csv -LiteralPath $catalogPath)) {
    $status = if ($screen.scope -eq 'excluded_repackaging') { 'EXCLUDED' }
        elseif ($screen.scope -eq 'baseline') { 'NOT_APPLICABLE' }
        else { 'NOT_STARTED' }
    $testStatus = if ($status -in @('EXCLUDED', 'NOT_APPLICABLE')) { 'NOT_APPLICABLE' } else { 'NOT_RUN' }
    $visualStatus = if ($screen.scope -eq 'native') { 'NOT_RUN' } else { 'NOT_APPLICABLE' }
    $deviation = switch ($screen.scope) {
        'excluded_repackaging' { 'DEV-009' }
        'external_system' { 'DEV-008' }
        'external_browser' { 'DEV-006' }
        'native' { 'DEV-001;DEV-002' }
        default { '' }
    }

    [pscustomobject][ordered]@{
        requirement_id = "SCR-$($screen.screen_id.Split('_')[0])"
        audit_requirement = "$($screen.screen_name) / $($screen.state_name)"
        audit_screen_ids = $screen.screen_id
        surface_group = $screen.unique_surface_group
        scope = $screen.scope
        classification = $screen.classification
        evidence_references = "$($screen.screenshot);$($screen.ui_xml);screens/screen-specs/$($screen.screen_id).json"
        implementation_status = $status
        implementation_reference = ''
        automated_test_reference = ''
        test_status = $testStatus
        validation_reference = ''
        visual_status = $visualStatus
        known_deviation_ids = $deviation
        notes = $screen.notes
    }
}

$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
Write-Host "Created $OutputPath with $($rows.Count) traceability rows."
