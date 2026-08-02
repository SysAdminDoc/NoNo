# Changelog

All notable changes to this project are documented here.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

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

### Added

- `scripts/run-unit-tests.ps1` and `scripts/run-ui-tests.ps1` parse their JUnit XML output
  into `validation/reports/unit-test-results.json` and
  `validation/reports/instrumentation-test-results.json`, and fail when the recorded
  result is not a pass. These summaries are the evidence the traceability matrix reads.
- `Get-JUnitSummary`, `Save-TestSummary`, and `Get-TestSummaryStatus` helpers in
  `scripts/Common.ps1`.
- `data/SignalPreferences.kt` centralises preference-store construction, and
  `SignalPreferencesTest` covers healthy round-trip, recovery from a deliberately corrupted
  file, writability after recovery, and the view model's read guard.
