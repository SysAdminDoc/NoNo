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
- JDK 17 or newer. `JAVA_HOME` is honoured when set; Android Studio's bundled JBR works.
- Android SDK platform tools and an authorized ADB device
- Python 3 for screenshot comparison: `py -3 -m pip install -r scripts/requirements.txt`

All device commands require an explicit serial when more than one device is connected.
`check-environment.ps1` fails when the device does not match the reference environment
recorded in `../app-audit/device/device-environment.json`, because baseline captures are
only comparable against a matching device. Pass `-AllowMismatch` to downgrade to a warning.

## Build, install, and launch

From the `replica-app` directory of your clone:

```powershell
.\scripts\check-environment.ps1 -Serial emulator-5554
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1 -Serial emulator-5554
.\scripts\launch-replica.ps1 -Serial emulator-5554
```

The build artifact is `app\build\outputs\apk\debug\app-debug.apk`. Copy it to `dist\SignalRules-debug.apk` when freezing a deliverable; the APK itself is not tracked in git, but `dist\SHA256SUMS.txt` is, so a downloaded artifact can still be verified.

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

## What this build does not do

The reconstruction reproduces the audited interface. It is not a working notification manager,
and the UI now says so at each control rather than leaving the reader to infer it:

- **There is no rule engine.** The notification listener records nothing but a package name, a
  timestamp, and a counter. No rule is ever evaluated and no notification is ever changed.
- **No action is executed.** Every entry in the action catalog can be selected and saved; none
  of them do anything.
- **Backup, import, export, and launcher shortcuts are not implemented.** No file format has
  been defined, so those rows are marked unavailable instead of opening a picker that discards
  its result.
- **Only the dark theme exists**, and the app ships no translated resources, so the Theme and
  Language rows are marked unavailable.
- **History is populated only with package/key/time metadata** by the listener; debug captures
  remain available for deterministic audit states, and notification content is never persisted.

The runtime boundary now records only package/key/time metadata in a bounded Room queue. Android
15 sensitive-notification redaction is treated as provenance (`content hidden by system`) and is
never matchable as real text. Companion-device listener exemptions are intentionally out of scope
for this local reconstruction; no special permission or companion association is requested.

Settings that would depend on the absent action engine are shown disabled with the reason
inline. See `docs\known-deviations.md` for the full list and `..\ROADMAP.md` for what is
planned.

## Documentation

- `docs\rebuild-plan.md` — scope and implementation order
- `docs\audit-traceability-matrix.csv` — all 88 audit rows mapped to implementation and evidence
- `docs\architecture.md` — clean-room architecture
- `docs\testing-guide.md` — repeatable QA procedure
- `validation\reports\final-coverage-report.md` — measured outcome and gaps
