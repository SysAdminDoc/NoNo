# Signal Rules

Signal Rules is an independent clean-room Android reconstruction of the native interface and observable behavior documented in `../app-audit`. It does not use the original package identity, proprietary source, branding, illustrations, font files, signing material, private data, or APK assets.

## Project identity

- Display name: **Signal Rules**
- Application ID: `com.anm.signalrules.reconstruction`
- Debug package: `com.anm.signalrules.reconstruction.debug`
- Android support: API 24 and newer; target SDK 35; compiled with SDK 36
- Reference device: Android 16/API 36, 1080 × 2400 px, 420 dpi, `en-US`, font scale 1.0, gesture navigation
- Backend: none. Audited data-dependent behavior is local and deterministic.

## Requirements

- Windows PowerShell 5.1 or newer
- JDK 17 or newer (`D:\tools\jdk21` is auto-detected on this workstation)
- Android SDK platform tools and an authorized ADB device
- Python 3 with Pillow and NumPy for screenshot comparison

All device commands require an explicit serial when more than one device is connected.

## Build, install, and launch

From `Z:\ANM-Android_Notification_Manager\replica-app`:

```powershell
.\scripts\check-environment.ps1 -Serial emulator-5554
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1 -Serial emulator-5554
.\scripts\launch-replica.ps1 -Serial emulator-5554
```

The build artifact is `app\build\outputs\apk\debug\app-debug.apk`. The frozen deliverable is copied to `dist\SignalRules-debug.apk` with a SHA-256 checksum.

## Tests and validation

```powershell
.\scripts\run-unit-tests.ps1
.\scripts\run-lint.ps1
.\scripts\run-ui-tests.ps1 -Serial emulator-5554
.\scripts\run-visual-validation.ps1 -Serial emulator-5554
.\scripts\run-full-validation.ps1 -Serial emulator-5554
```

The visual command intentionally returns a nonzero result while any configured threshold miss remains. See `validation\reports\visual-validation-report.md` and `validation\reports\final-coverage-report.md` before interpreting that exit code.

## Reproducing audited states

Every native audit capture can be opened through a debug-only intent extra:

```powershell
.\scripts\launch-replica.ps1 -Serial emulator-5554 -State 062_rule_builder_complete
```

The complete 76-state mapping is in `test-data\states\audit-state-map.csv`. Release builds do not accept this QA override.

## Clean-room and asset notes

`authorized-assets` contains no third-party originals. The launcher art, product identity, onboarding visuals, Explore visuals, and editorial summaries are independently created replacements. AndroidX/platform icons and fonts are used under their respective dependency/platform terms. Audit screenshots exist only as validation evidence and are never packaged into the APK.

The notification-changing action engine is a safe local simulation. Android permission/settings and file-picker surfaces are invoked through public intents. Backup serialization, scheduled backup, and launcher shortcut publication remain unimplemented because their formats/effects were not safely observable. See `docs\known-deviations.md` for the complete list.

## Documentation

- `docs\rebuild-plan.md` — scope and implementation order
- `docs\audit-traceability-matrix.csv` — all 88 audit rows mapped to implementation and evidence
- `docs\architecture.md` — clean-room architecture
- `docs\testing-guide.md` — repeatable QA procedure
- `validation\reports\final-coverage-report.md` — measured outcome and gaps
