# Changelog

All notable changes to this project are documented here.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-08-02

### Added

- Added Android 15 sensitive-notification provenance, preventing system redaction placeholders
  from entering future rule matching and marking history as content hidden by the system.
- Added bounded listener ingestion, Room-backed metadata history, transactional retention pruning,
  drop/failure diagnostics, and 30-day/3-month/6-month boundary tests. Companion-device listener
  exemptions remain intentionally out of scope for this reconstruction.
- Added a reproducible-release gate that builds two clean checkouts with a pinned JDK, honors
  `SOURCE_DATE_EPOCH`, and compares the unsigned release APK hashes outside the repository.
- Added a pure dry-run rule evaluator with redaction-aware condition traces, deterministic
  specificity and priority conflict resolution, and an explicit not-executed action result.
- The history “Create rule” action now selects the tapped record, pre-fills app and safely
  derivable phrase fields, and explains when redaction or metadata-only storage prevents copying
  notification content.
- Corrected the DataStore test fixture to start with an absent backing file, matching DataStore's
  create-on-first-write contract and removing false corruption/race failures from the local suite.
- Preserved Android notification group keys and summary provenance in metadata history, added a
  Room migration, and made group summaries ineligible for future duplicate rule evaluation.
- Made portable transfer encoding API-24-safe by using Kotlin's platform-independent Base64
  implementation instead of the API-26-only Java encoder.
- Rules now persist stable Android package IDs separately from display labels, migrate known
  legacy app selections, and pass package identity through the dry-run matcher.
- Restricted notification-listener binding to the system, declared explicit listener filter
  defaults, removed unused notification/Doze permissions, and clarified the local redaction-only
  capability during onboarding.
- History search and retention now run through bounded Room queries with metadata selectors,
  explicit loading/empty/error/retry states, and immediate pruning when the retention setting
  changes. The metadata-only build no longer claims to show only “today” or invent rule-action
  history states.
- Notification activity now shows a pure dry-run explanation for selected metadata records,
  including content provenance, unmet conditions, conflict winners, priority overrides, and an
  explicit `NOT_EXECUTED` result.
- Listener queue counters and failure timestamps now persist as redacted Room diagnostics and
  restore into the health surface after restart. The warning banner requests a safe rebind while
  directing the user to notification-access settings for recovery.
- Listener shutdown now fences new callbacks, drains the bounded worker before closing Room, and
  makes teardown idempotent. Rebind requests are limited to the platform’s disconnected window.
- History metadata now includes nullable channel IDs and supports package, channel, group,
  content-provenance, and group-summary filters through bounded Room queries and migrated schema.
- Added a deterministic build-policy task and CI gates for pinned repositories, wrapper/catalog
  versions, dependency hash coverage, strict verification, and high-severity dependency advisories.
- Reconciled the root and app READMEs with the metadata-only Room runtime, redaction-aware dry-run
  evaluator, schema versions, bounded history filters, and intentionally disabled live actions.
- Added API 24/35/36 redaction fixtures covering available, unavailable, explicit-sensitive,
  marker, package-identity, and metadata-only dry-run behavior without payload logging.
- Added checked-in Room schema fixtures for versions 1–4, an instrumented all-version migration
  test, and v1–v3 RuleCodec golden fixtures covering normalization and unsupported versions.
- Connected encrypted rule transfer to Android's Storage Access Framework with passphrase prompts,
  import preview, keep-or-replace conflict resolution, cancellation/error recovery, and explicit
  exclusion of notification history.

## [1.1.0] - 2026-08-01

### Fixed

- `check-environment.ps1` asserts instead of narrating. It printed device properties and always
  reported success, so a mismatched emulator silently invalidated every comparison that
  followed. It now fails when API level, resolution, density, locale, or font scale differ from
  the reference device, with `-AllowMismatch` to downgrade that to a warning.
- The similarity threshold is no longer defaulted in two places to a value that disagreed with
  the authoritative one. `compare_images.py` and `compare-screen.ps1` defaulted to 0.90 while
  `validation/screen-validation-matrix.csv` specifies 0.85; both now require the caller to pass
  it.
- Screenshot comparison resolves Python through the `py` launcher before falling back to
  `python.exe`, and `scripts/requirements.txt` pins Pillow and NumPy.
- Removed `test-fixtures/` and `test-states/`, byte-identical duplicates of `test-data/`, and
  repointed the state map's `fixture_source` column at the surviving copy.
- Replaced the stale `Z:\` build path and the retired `D:	ools\jdk21` reference in
  `replica-app/README.md`.
- Cleared the low-severity correctness defects: the Explore article accent no longer indexes a
  fixed four-element list by article index (a fifth article threw
  `IndexOutOfBoundsException`), back and volume icons use their `AutoMirrored` variants for RTL,
  the deprecated `Divider` is now `HorizontalDivider`, the deprecated
  `SOFT_INPUT_ADJUST_RESIZE` call was dropped since it is ignored under the edge-to-edge
  enforcement targetSdk 36 makes mandatory, and the empty-state copy shown from the non-empty
  rules branch was corrected.
- Keyboard focus is requested once after a frame instead of three blind retries.
  `FocusRequester.requestFocus()` throws when its node is not attached, which the retry loop
  invited by firing before a Dialog's subcomposition existed. The four duplicated loops are now
  one guarded helper. The debug and release builds compile with zero deprecation warnings.
- The rule dialogs no longer discard the user's choice. Match type, extra properties, filter
  operator, "Enable for", priority, and folder all dismissed without applying anything, and the
  folder dialog wrote into the rename dialog's field before throwing it away. Each selection is
  now applied to the addressed rule or draft, persisted, and reflected in the rule builder and
  on the rule card. Evaluation semantics are still absent - the audit records rule precedence
  and folder behaviour as UNKNOWN - so these are stored and displayed, not acted on.
- The rule-builder overflow menu addresses the rule being edited rather than whichever rule
  happened to be first.
- Bottom-anchored controls on full-screen editors no longer render underneath the navigation
  bar. Only the root route has a bottom bar, and only that bar applied the navigation-bar
  inset, so "Pick all apps" and "Apply filter" sat under the system navigation.
- The history list honours its search field and segmented filter. Both were wired to state
  that the list never read, so typing a query or switching to Rule-triggered changed nothing.
- The rule card renders each rule's own action instead of a hardcoded mute glyph, and uses the
  unit-tested sentence renderer rather than a second, divergent copy of the same string.
- The notification listener now recovers from being unbound. It had no
  `onListenerConnected`/`onListenerDisconnected` overrides and never called `requestRebind`,
  so a routine platform unbind - an app update, a service crash, an OEM background kill -
  silently ended all functionality until the user toggled notification access by hand.
  Rebind is requested on disconnect and on every app resume.
- Notification access is re-checked on every resume, not only during onboarding. Access
  revoked after setup was previously invisible, leaving the app presenting a working rule list
  while the listener was dead.
- The listener no longer performs disk I/O on the main thread. `onNotificationPosted` ran two
  `getSharedPreferences` calls and a read-modify-write for every notification from every app on
  the device, into a file nothing read.
- All notification-listener settings intents are guarded and route through one helper, which
  prefers the per-app `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` screen on API 30+ and
  falls back to the global list. Two call sites previously launched an unguarded intent that
  throws `ActivityNotFoundException` on images lacking the activity or inside a work profile.
- The Settings screen no longer advertises behaviour the build does not have. Import, Export,
  Automatic backups, Clear shortcuts, Restore batch, Translate, Contact support, Open
  community, Theme, Language, and every switch that would depend on the absent action engine
  are shown disabled with the reason stated inline. Import/Export previously opened a folder
  picker whose result was discarded, and several rows only raised a toast.
- `Delete all rules` is implemented instead of raising a toast that said it was disabled.
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

- `targetSdk` moved from 35 to 36. System bar colour attributes were removed from the theme
  (no-ops from API 35; `enableEdgeToEdge` owns them), predictive back is declared explicitly,
  and the fixed-orientation lock was dropped since API 36 ignores it on displays 600dp and
  wider and the opt-out property stops working at API 37. `androidx.activity:activity-compose`
  moved 1.9.3 to 1.13.0, which was roughly two years of skew against the Compose BOM in use.
- The 19.7 MB debug APK is no longer tracked in git. `dist/SHA256SUMS.txt` stays tracked so a
  downloaded artifact can still be verified.

- The audit-state capture harness moved into variant source sets. `app/src/debug` holds the
  state table and the intent-extra reader; `app/src/release` links a no-op twin. The release
  DEX no longer contains the `replica_state` extra or any capture id, so production behaviour
  cannot depend on QA scaffolding.

### Added

- MIT `LICENSE` at the repository root.

- A listener health banner on the Rules screen states when rules are not running, why, and how
  long since the last notification was seen, and links straight to notification access. It is
  announced as a polite live region.

- `scripts/run-unit-tests.ps1` and `scripts/run-ui-tests.ps1` parse their JUnit XML output
  into `validation/reports/unit-test-results.json` and
  `validation/reports/instrumentation-test-results.json`, and fail when the recorded
  result is not a pass. These summaries are the evidence the traceability matrix reads.
- `Get-JUnitSummary`, `Save-TestSummary`, and `Get-TestSummaryStatus` helpers in
  `scripts/Common.ps1`. The runners force task execution and refuse to record evidence when
  Gradle reports success without producing results, so an UP-TO-DATE task cannot be mistaken
  for a suite that ran.
- `ListenerHealthTest` covers the published connection state, including that it starts
  `UNKNOWN` rather than claiming to be connected.
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
