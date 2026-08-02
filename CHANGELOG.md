# Changelog

All notable changes to this project are documented here.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Notification-derived data is excluded from automatic backup and device transfer. The app
  declared `allowBackup="true"` with neither `dataExtractionRules` nor `fullBackupContent`, so
  its preference store was eligible for upload to the user's Google Drive. Both rule sets are
  now declared, and the store itself moved under `noBackupFilesDir` so the exclusion does not
  depend on backup rules that older platforms and OEM agents honour inconsistently. An
  existing store is migrated across on first run.
- Notification history search and the phrase/extras/group condition selector work in release
  builds. Both were gated on audit-capture id strings that only the debug-only QA override
  could set, so the search icon and the condition chooser were inert in a shipping build
  while the UI still offered them. They are now driven by real UI state
  (`historySearchActive`, `phraseInputVisible`), and rule-builder validation is carried by a
  `validationError` field instead of being inferred from a capture id and a snackbar message.
- Rules are addressed by id and persisted as a list. Saving replaced the entire collection
  with the single edited rule, and toggle/rename/delete all operated on `rules.first()`
  regardless of which card was touched, so duplicating a rule and then toggling the copy
  silently destroyed the original. Every mutation now targets one `SignalRule.id`, and the
  store holds the whole list under a versioned payload rather than six flat scalar keys.
  Rules written by the previous build are migrated on first read.
- Preference storage now survives a corrupt or unreadable backing file. `MainViewModel`
  built its DataStore with no `corruptionHandler` and read it with a bare
  `viewModelScope.launch`, so a truncated `signal_rules.preferences_pb` threw
  `CorruptionException` on every launch and — because the bad file persisted — bricked the
  app permanently. Reads now fall back to defaults, the store is rebuilt via
  `ReplaceFileCorruptionHandler`, the user is told their settings were reset, and every
  write is guarded so an IO failure costs one unsaved change instead of the process.
- Traceability status is now derived from recorded machine-readable evidence instead of
  being asserted. `scripts/finalize-documentation.ps1` previously wrote `test_status = PASS`
  unconditionally for every native row, so `docs/audit-traceability-matrix.csv` certified
  itself. Status is now computed from the per-screen visual results plus durable suite
  summaries emitted by the test runners; missing or failing evidence yields `NOT_RUN` or
  `FAIL`. Regenerating with no test artifacts present now produces zero `PASS` values.
  As a direct consequence the 76 native rows currently read `NOT_RUN`: the instrumented
  suite has not been executed against the reference device in this checkout, and the
  matrix no longer claims otherwise.
- `scripts/finalize-documentation.ps1` no longer crashes on screen ids whose numeric
  prefix is not exactly three digits, and validates its required inputs before writing.

### Changed

- The audit-state capture harness moved into variant source sets. `app/src/debug` holds the
  state table and the intent-extra reader; `app/src/release` links a no-op twin. The release
  DEX no longer contains the `replica_state` extra or any capture id, so production behaviour
  cannot depend on QA scaffolding.

### Added

- `scripts/run-unit-tests.ps1` and `scripts/run-ui-tests.ps1` parse their JUnit XML output
  into `validation/reports/unit-test-results.json` and
  `validation/reports/instrumentation-test-results.json`, and fail when the recorded
  result is not a pass. These summaries are the evidence the traceability matrix reads.
- `Get-JUnitSummary`, `Save-TestSummary`, and `Get-TestSummaryStatus` helpers in
  `scripts/Common.ps1`.
- `AuditStatesTest` pins the debug capture harness, including that 033 resolves to the
  condition chooser and 034/041 to the text input, so the source-set split cannot silently
  break state reproduction.
- `model/RuleOperations.kt` holds the rule-list algebra as pure functions, and
  `data/RuleCodec.kt` encodes the versioned store; a payload from a newer build, malformed
  JSON, or duplicate ids all degrade to a safe fallback instead of throwing.
  `RuleOperationsTest` and `RuleCodecTest` cover both, including the duplicate-then-toggle
  sequence that used to lose data.
- Rule priority and folder selections are now applied to the addressed rule instead of
  being discarded when the dialog closes.
- `data/SignalPreferences.kt` centralises preference-store construction, and
  `SignalPreferencesTest` covers healthy round-trip, recovery from a deliberately corrupted
  file, writability after recovery, and the view model's read guard.
