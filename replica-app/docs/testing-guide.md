# Testing guide

## Environment

Use PowerShell 5.1 or newer from the `replica-app` directory. Supply `-Serial emulator-5554` for every device-aware script. The environment check rejects an absent or unauthorized device and validates ADB, JDK, Android version, size, and density.

## Quality gates

1. Run `scripts\run-unit-tests.ps1` for deterministic rule, validation, action-catalog, and history logic.
2. Run `scripts\run-lint.ps1` for Android lint.
3. Run `scripts\build-debug.ps1` and `scripts\install-debug.ps1 -Serial emulator-5554`.
4. Run `scripts\run-ui-tests.ps1 -Serial emulator-5554` for on-device Compose semantics and launch coverage.
5. Run `scripts\run-visual-validation.ps1 -Serial emulator-5554` for all 76 native audit states.
6. Run `scripts\run-full-validation.ps1 -Serial emulator-5554` to execute the complete sequence and write a timestamped JSON log.

The visual gate is expected to return nonzero for the eight documented identity/editorial states. This is not a capture failure. Inspect each metrics JSON and the final coverage report.

## Debug state reproduction

Use `scripts\launch-replica.ps1 -Serial emulator-5554 -State SCREEN_ID`. Valid IDs are listed in `test-data/states/audit-state-map.csv`. The extra is consumed only when `BuildConfig.DEBUG` is true.

To capture one state:

```powershell
.\scripts\capture-replica-screen.ps1 -Serial emulator-5554 -ScreenId 062_rule_builder_complete -OutFile .\validation\current\062_rule_builder_complete.png -Force
```

To compare it:

```powershell
.\scripts\compare-screen.ps1 -ScreenId 062_rule_builder_complete -Baseline .\validation\baseline\062_rule_builder_complete.png -Current .\validation\current\062_rule_builder_complete.png -Threshold 0.85 -Mask .\validation\masks\system-bars.json
```

## Artifacts

- Baselines: `validation/baseline`
- Replica captures: `validation/current`
- Metrics, side-by-side images, raw diffs, and heatmaps: `validation/diffs`
- 50% overlays: `validation/overlays`
- Mask definitions and reasons: `validation/masks`
- Stable reports: `validation/reports`
- Timestamped runner logs: `validation/logs`
- Gradle test/lint HTML: `app/build/reports`

## Safe reset

`scripts\reset-replica.ps1 -Serial emulator-5554 -Force` clears only `com.sysadmindoc.nono.debug`. It never touches the audited package. Permission-flow validation changes were app-specific and were restored after testing.
