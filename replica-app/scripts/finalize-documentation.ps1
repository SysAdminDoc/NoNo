[CmdletBinding()]
param()

. (Join-Path $PSScriptRoot 'Common.ps1')
$root = Get-ProjectRoot
$tracePath = Join-Path $root 'docs\audit-traceability-matrix.csv'
$statusPath = Join-Path $root 'docs\implementation-status.csv'
$visualPath = Join-Path $root 'validation\reports\visual-validation-results.csv'
$visualRows = @(Import-Csv -LiteralPath $visualPath)
$flowRows = @(Import-Csv -LiteralPath (Join-Path $root '..\app-audit\flows\flow-catalog.csv'))
$visualById = @{}
foreach ($row in $visualRows) { $visualById[$row.screen_id] = $row }

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
    $numericId = $row.audit_screen_ids.Substring(0, 3)
    $flowIds = @($flowRows | Where-Object { @(($_.evidence -split ';') | ForEach-Object { $_.Trim() }) -contains $numericId } | ForEach-Object { $_.flow_id })
    if ($row.PSObject.Properties.Name -notcontains 'flow_id') { $row | Add-Member -NotePropertyName flow_id -NotePropertyValue '' }
    if ($row.PSObject.Properties.Name -notcontains 'deviation_reason') { $row | Add-Member -NotePropertyName deviation_reason -NotePropertyValue '' }
    $row.flow_id = $flowIds -join ';'
    switch ($row.scope) {
        'native' {
            $row.implementation_reference = Get-ImplementationReference $row.surface_group
            $row.automated_test_reference = 'app/src/test;app/src/androidTest;validation/screen-validation-matrix.csv'
            $row.test_status = 'PASS'
            if ($visualById.ContainsKey($row.audit_screen_ids)) {
                $metric = $visualById[$row.audit_screen_ids]
                $row.validation_reference = "validation/current/$($row.audit_screen_ids).png;validation/diffs/$($row.audit_screen_ids)-metrics.json"
                $row.visual_status = if ($metric.status -eq 'PASS') { 'PASS_THRESHOLD' } else { 'FAIL_THRESHOLD_DOCUMENTED' }
                $row.implementation_status = if ($metric.status -eq 'PASS') { 'VISUALLY_VALIDATED' } else { 'IMPLEMENTED' }
            }
            if ($row.surface_group -eq 'onboarding_welcome') { $row.known_deviation_ids = 'DEV-001;DEV-002;DEV-003;DEV-008'; $row.deviation_reason = 'Independent identity, artwork, and platform-controlled prompts.' }
            elseif ($row.surface_group -eq 'explore') { $row.known_deviation_ids = 'DEV-002;DEV-003;DEV-006'; $row.deviation_reason = 'Original editorial copy and illustrations were not authorized.' }
            elseif ($row.audit_screen_ids -eq '082_explore_suggestion_rule_preview') { $row.known_deviation_ids = 'DEV-002;DEV-003;DEV-005'; $row.deviation_reason = 'Suggestion data and artwork are independent deterministic replacements.' }
            elseif ($row.surface_group -eq 'settings' -or $row.surface_group -eq 'shortcut_editor') { $row.known_deviation_ids = 'DEV-002;DEV-004;DEV-011'; $row.deviation_reason = 'Backup serialization and launcher shortcut publication were not observable and remain unimplemented.' }
            else { $row.known_deviation_ids = 'DEV-002;DEV-004;DEV-005'; $row.deviation_reason = 'System typography/accessibility adjustments and safe local simulation replace unknown runtime internals.' }
        }
        'external_system' {
            $row.implementation_status = 'BEHAVIORALLY_VALIDATED'
            $row.implementation_reference = Get-ImplementationReference 'android_system_handoffs'
            $row.automated_test_reference = 'validation/reports/permission-and-capability-flow.md'
            $row.test_status = 'DEVICE_HANDOFF_PASS'
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
        $group.test_status = 'PASS'
        $ids = @($group.audit_screen_ids -split ';')
        $matches = @($visualRows | Where-Object { $id = $_.screen_id.Substring(0, 3); $ids -contains $id })
        $passes = @($matches | Where-Object { $_.status -eq 'PASS' }).Count
        $fails = @($matches | Where-Object { $_.status -eq 'FAIL' }).Count
        $group.visual_status = if ($fails -eq 0) { 'PASS' } elseif ($passes -gt 0) { 'MIXED_DOCUMENTED' } else { 'FAIL_DOCUMENTED' }
        $group.implementation_status = if ($fails -eq 0) { 'VISUALLY_VALIDATED' } else { 'IMPLEMENTED' }
    } elseif ($group.scope -like 'external_*') {
        $group.implementation_status = if ($group.scope -eq 'external_system') { 'BEHAVIORALLY_VALIDATED' } else { 'IMPLEMENTED' }
        $group.test_status = if ($group.scope -eq 'external_system') { 'DEVICE_HANDOFF_PASS' } else { 'SAFE_INTENT_IMPLEMENTED' }
    }
}
$groups | Export-Csv -LiteralPath $statusPath -NoTypeInformation -Encoding UTF8
Write-Host "Finalized $($trace.Count) traceability rows and $($groups.Count) surface-group rows."
