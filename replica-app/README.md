# Signal Rules

Signal Rules is an independent clean-room Android reconstruction of the native interface and observable behavior documented in `../app-audit`. It does not use the original package identity, proprietary source, branding, illustrations, font files, signing material, private data, or APK assets.

## Project identity

- Display name: **Signal Rules**
- Application ID: `com.anm.signalrules.reconstruction`
- Debug package: `com.anm.signalrules.reconstruction.debug`
- Android support: API 24 and newer; target SDK 36; compiled with SDK 36
- Reference device: Android 16/API 36, 1080 × 2400 px, 420 dpi, `en-US`, font scale 1.0, gesture navigation
- Backend: none. Notification metadata, rules, and diagnostics are local and deterministic.
  The current Room history schema is version 4; the DataStore rule payload is version 3.

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

Gradle dependency verification is enabled through `gradle\verification-metadata.xml`, which
records SHA-256 hashes for the resolved artifacts. Refresh it only after reviewing the dependency
diff with `gradlew --write-verification-metadata sha256 help`; PGP key verification is not enabled
until the project owner reviews and approves the required signer keys. Run
`.\gradlew.bat verifyBuildPolicy` from `replica-app` to validate repository, wrapper, catalog,
and hash-coverage policy locally; the same policy and strict verification run in CI.

## Reproducing audited states

Every native audit capture can be opened through a debug-only intent extra:

```powershell
.\scripts\launch-replica.ps1 -Serial emulator-5554 -State 062_rule_builder_complete
```

The complete 76-state mapping is in `test-data\states\audit-state-map.csv`. Release builds do not accept this QA override.

## Clean-room and asset notes

`authorized-assets` contains no third-party originals. The launcher art, product identity, onboarding visuals, Explore visuals, and editorial summaries are independently created replacements. AndroidX/platform icons and fonts are used under their respective dependency/platform terms. Audit screenshots exist only as validation evidence and are never packaged into the APK.

## What this build does not do

The reconstruction reproduces the audited interface while providing a safe local metadata
runtime. It is not a live notification action manager, and the UI states the boundary at each
control rather than leaving the reader to infer it:

- **Live evaluation and actions are absent.** The app includes a pure, redaction-aware dry-run
  evaluator for history activity and rule explanations; it never changes a notification, sound,
  setting, or `PendingIntent`.
- **No notification content is stored.** The listener records package identity, notification key,
  posted time, channel/group/summary metadata, content provenance, bounded ingestion counters, and
  failure timestamps. Titles and bodies are never persisted.
- **Automatic backups and launcher shortcuts are not implemented.** Encrypted rule import/export
  is available through Android's Storage Access Framework, with a passphrase, preview, conflict
  choice, cancellation/error handling, and no notification history in the file. Automatic backup
  scheduling remains unavailable.
- **Only the dark theme exists**, and the app ships no translated resources, so the Theme and
  Language rows are marked unavailable.
- **History is bounded and queryable.** Search and package/channel/group/content-provenance and
  summary filters are backed by Room migrations and explicit loading/error/retry states. Debug
  captures remain available for deterministic audit states.

The runtime boundary records metadata in a bounded Room queue. Android 15 sensitive-notification
redaction is treated as provenance (`content hidden by system`) and is never matchable as real
text. Preferences and history live under the no-backup boundary; listener diagnostics restore
after process restart. Companion-device listener exemptions are intentionally out of scope for
this local reconstruction; no special permission or companion association is requested.

Settings that would depend on the absent action engine are shown disabled with the reason
inline. See `docs\known-deviations.md` for the full list and `..\ROADMAP.md` for what is
planned.

## Documentation

- `docs\rebuild-plan.md` — scope and implementation order
- `docs\audit-traceability-matrix.csv` — all 88 audit rows mapped to implementation and evidence
- `docs\architecture.md` — clean-room architecture
- `docs\testing-guide.md` — repeatable QA procedure
- `validation\reports\final-coverage-report.md` — measured outcome and gaps
