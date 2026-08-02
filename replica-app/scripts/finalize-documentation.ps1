[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$tracePath = Join-Path $root 'docs\audit-traceability-matrix.csv'
$statusPath = Join-Path $root 'docs\implementation-status.csv'
$visualPath = Join-Path $root 'validation\reports\visual-validation-results.csv'
$flowCatalogPath = Join-Path $root '..\app-audit\flows\flow-catalog.csv'
foreach ($required in @($tracePath, $statusPath, $flowCatalogPath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required input is missing: $required" }
}
$visualRows = if (Test-Path -LiteralPath $visualPath -PathType Leaf) { @(Import-Csv -LiteralPath $visualPath) } else { @() }
if ($visualRows.Count -eq 0) { Write-Host 'No visual validation results found; native rows will be recorded as NOT_RUN.' }
$flowRows = @(Import-Csv -LiteralPath $flowCatalogPath)
$visualById = @{}
foreach ($row in $visualRows) { $visualById[$row.screen_id] = $row }

# Suite-level evidence. Absent summary files mean the suite was never run in
# this checkout, which must surface as NOT_RUN rather than an asserted PASS.
$unitStatus = Get-TestSummaryStatus (Join-Path $root 'validation\reports\unit-test-results.json')
$instrumentationStatus = Get-TestSummaryStatus (Join-Path $root 'validation\reports\instrumentation-test-results.json')
Write-Host "Suite evidence: unit=$unitStatus instrumentation=$instrumentationStatus"

function Get-EvidenceReference {
    param([string]$ScreenId)
    $found = @()
    if (Test-Path -LiteralPath (Join-Path $root 'validation\reports\unit-test-results.json')) { $found += 'validation/reports/unit-test-results.json' }
    if (Test-Path -LiteralPath (Join-Path $root 'validation\reports\instrumentation-test-results.json')) { $found += 'validation/reports/instrumentation-test-results.json' }
    if (-not [string]::IsNullOrWhiteSpace($ScreenId) -and $visualById.ContainsKey($ScreenId)) { $found += "validation/diffs/$ScreenId-metrics.json" }
    if ($found.Count -eq 0) { return 'none' }
    return ($found -join ';')
}

# A native row is only PASS when per-screen visual evidence exists and both
# suites recorded a pass. Any missing or failing input degrades the verdict.
function Resolve-NativeTestStatus {
    param([string]$ScreenId)
    if (-not $visualById.ContainsKey($ScreenId)) { return 'NOT_RUN' }
    if ($unitStatus -eq 'FAIL' -or $instrumentationStatus -eq 'FAIL') { return 'FAIL' }
    if ($unitStatus -ne 'PASS' -or $instrumentationStatus -ne 'PASS') { return 'NOT_RUN' }
    if ($visualById[$ScreenId].status -eq 'FAIL') { return 'FAIL_THRESHOLD_DOCUMENTED' }
    return 'PASS'
}

# Screen ids are '<NNN>_<name>'; take the numeric prefix without assuming width.
function Get-ScreenIdPrefix {
    param([string]$ScreenId)
    if ([string]::IsNullOrWhiteSpace($ScreenId)) { return '' }
    $match = [regex]::Match($ScreenId, '^(\d+)')
    if ($match.Success) { return $match.Groups[1].Value }
    return $ScreenId
}

function Test-EvidenceFilesExist {
    param([string[]]$RelativePaths)
    foreach ($relative in $RelativePaths) {
        if (-not (Test-Path -LiteralPath (Join-Path $root $relative))) { return $false }
    }
    return $true
}

function Get-ImplementationReference([string]$group) {
    switch ($group) {
        'onboarding_welcome' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/OnboardingScreen.kt' }
        'rules_home' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/RulesScreens.kt' }
        'explore' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/ExploreScreen.kt' }
        'history' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/HistoryScreens.kt' }
        'history_activity' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/HistoryScreens.kt' }
        'settings' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/SettingsScreen.kt' }
        'shortcut_editor' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/SettingsScreen.kt' }
        'app_selector' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/RulesScreens.kt' }
        'condition_builder' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/RulesScreens.kt' }
        'phrase_editor' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/RulesScreens.kt' }
        'nested_filter_group' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/RulesScreens.kt' }
        'action_selector' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/RulesScreens.kt' }
        'android_system_handoffs' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/OnboardingScreen.kt;app/src/main/java/com/anm/signalrules/reconstruction/ui/SettingsScreen.kt' }
        'external_browser_handoffs' { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/ExploreScreen.kt;app/src/main/java/com/anm/signalrules/reconstruction/ui/SettingsScreen.kt' }
        default { 'app/src/main/java/com/anm/signalrules/reconstruction/ui/SignalOverlay.kt' }
    }
}

$trace = @(Import-Csv -LiteralPath $tracePath)
foreach ($row in $trace) {
    $numericId = Get-ScreenIdPrefix $row.audit_screen_ids
    $flowIds = @($flowRows | Where-Object { @(($_.evidence -split ';') | ForEach-Object { $_.Trim() }) -contains $numericId } | ForEach-Object { $_.flow_id })
    if ($row.PSObject.Properties.Name -notcontains 'flow_id') { $row | Add-Member -NotePropertyName flow_id -NotePropertyValue '' }
    if ($row.PSObject.Properties.Name -notcontains 'deviation_reason') { $row | Add-Member -NotePropertyName deviation_reason -NotePropertyValue '' }
    $row.flow_id = $flowIds -join ';'
    switch ($row.scope) {
        'native' {
            $row.implementation_reference = Get-ImplementationReference $row.surface_group
            $row.automated_test_reference = Get-EvidenceReference $row.audit_screen_ids
            $row.test_status = Resolve-NativeTestStatus $row.audit_screen_ids
            if ($visualById.ContainsKey($row.audit_screen_ids)) {
                $metric = $visualById[$row.audit_screen_ids]
                $row.validation_reference = "validation/current/$($row.audit_screen_ids).png;validation/diffs/$($row.audit_screen_ids)-metrics.json"
                $row.visual_status = if ($metric.status -eq 'PASS') { 'PASS_THRESHOLD' } else { 'FAIL_THRESHOLD_DOCUMENTED' }
                $row.implementation_status = if ($metric.status -eq 'PASS') { 'VISUALLY_VALIDATED' } else { 'IMPLEMENTED' }
            } else {
                $row.validation_reference = 'none'
                $row.visual_status = 'NOT_RUN'
                $row.implementation_status = 'IMPLEMENTED'
            }
            if ($row.surface_group -eq 'onboarding_welcome') { $row.known_deviation_ids = 'DEV-001;DEV-002;DEV-003;DEV-008'; $row.deviation_reason = 'Independent identity, artwork, and platform-controlled prompts.' }
            elseif ($row.surface_group -eq 'explore') { $row.known_deviation_ids = 'DEV-002;DEV-003;DEV-006'; $row.deviation_reason = 'Original editorial copy and illustrations were not authorized.' }
            elseif ($row.audit_screen_ids -eq '082_explore_suggestion_rule_preview') { $row.known_deviation_ids = 'DEV-002;DEV-003;DEV-005'; $row.deviation_reason = 'Suggestion data and artwork are independent deterministic replacements.' }
            elseif ($row.surface_group -eq 'settings' -or $row.surface_group -eq 'shortcut_editor') { $row.known_deviation_ids = 'DEV-002;DEV-004;DEV-011'; $row.deviation_reason = 'Backup serialization and launcher shortcut publication were not observable and remain unimplemented.' }
            else { $row.known_deviation_ids = 'DEV-002;DEV-004;DEV-005'; $row.deviation_reason = 'System typography/accessibility adjustments and safe local simulation replace unknown runtime internals.' }
        }
        'external_system' {
            $row.implementation_reference = Get-ImplementationReference 'android_system_handoffs'
            # Device-handoff evidence is the captured UIAutomator dump set; without
            # those files there is nothing to substantiate the handoff.
            $handoffEvidence = @(
                'validation\reports\permission-step-0.xml',
                'validation\reports\permission-listener-settings.xml',
                'validation\reports\permission-complete-home.xml'
            )
            if (Test-EvidenceFilesExist $handoffEvidence) {
                $row.automated_test_reference = 'validation/reports/permission-step-0.xml;validation/reports/permission-listener-settings.xml;validation/reports/permission-complete-home.xml'
                $row.test_status = 'DEVICE_HANDOFF_PASS'
                $row.implementation_status = 'BEHAVIORALLY_VALIDATED'
            } else {
                $row.automated_test_reference = 'none'
                $row.test_status = 'NOT_RUN'
                $row.implementation_status = 'IMPLEMENTED'
            }
            $row.visual_status = 'NOT_APPLICABLE'
            $row.known_deviation_ids = if ($row.audit_screen_ids.StartsWith('026_')) { 'DEV-008;DEV-011' } else { 'DEV-008' }
            $row.deviation_reason = 'Android owns and renders the destination UI; the replica invokes the corresponding public intent.'
        }
        'external_browser' {
            $row.implementation_status = 'IMPLEMENTED'
            $row.implementation_reference = Get-ImplementationReference 'external_browser_handoffs'
            $row.test_status = 'SAFE_INTENT_IMPLEMENTED'
            $row.visual_status = 'NOT_APPLICABLE'
            $row.known_deviation_ids = 'DEV-006'
            $row.deviation_reason = 'External mutable article contents were out of native audit scope.'
        }
        'excluded_repackaging' { $row.deviation_reason = 'Explicitly excluded third-party distribution overlay.' }
    }
}
$trace | Export-Csv -LiteralPath $tracePath -NoTypeInformation -Encoding UTF8

$groups = @(Import-Csv -LiteralPath $statusPath)
foreach ($group in $groups) {
    if ($group.scope -eq 'native') {
        $ids = @($group.audit_screen_ids -split ';')
        $matches = @($visualRows | Where-Object { $ids -contains (Get-ScreenIdPrefix $_.screen_id) })
        $passes = @($matches | Where-Object { $_.status -eq 'PASS' }).Count
        $fails = @($matches | Where-Object { $_.status -eq 'FAIL' }).Count
        if ($matches.Count -eq 0) {
            $group.test_status = 'NOT_RUN'
            $group.visual_status = 'NOT_RUN'
            $group.implementation_status = 'IMPLEMENTED'
        } else {
            if ($unitStatus -eq 'FAIL' -or $instrumentationStatus -eq 'FAIL') { $group.test_status = 'FAIL' }
            elseif ($unitStatus -ne 'PASS' -or $instrumentationStatus -ne 'PASS') { $group.test_status = 'NOT_RUN' }
            elseif ($fails -gt 0) { $group.test_status = 'FAIL_THRESHOLD_DOCUMENTED' }
            else { $group.test_status = 'PASS' }
            $group.visual_status = if ($fails -eq 0) { 'PASS' } elseif ($passes -gt 0) { 'MIXED_DOCUMENTED' } else { 'FAIL_DOCUMENTED' }
            $group.implementation_status = if ($fails -eq 0) { 'VISUALLY_VALIDATED' } else { 'IMPLEMENTED' }
        }
    } elseif ($group.scope -like 'external_*') {
        $groupRows = @($trace | Where-Object { $_.scope -eq $group.scope })
        $groupStatuses = @($groupRows | ForEach-Object { $_.test_status } | Sort-Object -Unique)
        $group.test_status = if ($groupStatuses.Count -eq 1) { $groupStatuses[0] } elseif ($groupStatuses -contains 'NOT_RUN') { 'NOT_RUN' } else { 'MIXED_DOCUMENTED' }
        $group.implementation_status = if ($group.test_status -eq 'NOT_RUN') { 'IMPLEMENTED' } elseif ($group.scope -eq 'external_system') { 'BEHAVIORALLY_VALIDATED' } else { 'IMPLEMENTED' }
    }
}
$groups | Export-Csv -LiteralPath $statusPath -NoTypeInformation -Encoding UTF8
Write-Host "Finalized $($trace.Count) traceability rows and $($groups.Count) surface-group rows."
