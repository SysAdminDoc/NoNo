# Changelog

All notable changes to this project are documented here.
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.3.2] - 2026-08-29

### Changed

- New app icon: adaptive, themed (monochrome) and legacy variants regenerated from the 2026-08 icon set.

- Moved the build to Gradle 9.7.1, Android Gradle Plugin 9.3.2, Kotlin 2.4.10, KSP 2.3.11,
  Compose BOM 2026.08.00, kotlinx.serialization 1.11.0 and Lifecycle 2.11.0. Gradle 8.11.1 predated
  the fixes for two 2026 repository-fallback advisories, which was the reason to move. AGP 9 brings
  its own Kotlin, so the separate Kotlin plugin is gone, and Compose 1.12 requires compiling against
  API 37; the app still targets API 36. Dependency verification covers all 1,471 resolved artifacts.

- Pinned the Gradle daemon to Java 21 through `gradle/gradle-daemon-jvm.properties`, so the build
  no longer depends on what `JAVA_HOME` happens to point at. Handing Gradle the Android Studio JBR
  used to fail while creating tasks with a message that named neither Java nor the JDK. The build
  policy check now fails if that pin is removed, and the PowerShell scripts pick a supported JDK
  instead of trusting `JAVA_HOME`.

### Documentation

- Corrected the capability boundary in the README: dark, light, and system themes all ship and the
  choice is persisted. Only the Language row stays unavailable.
- Rewrote `docs/architecture.md`, which still described a prototype that stored a package name, a
  timestamp, and a counter for a single rule. It now covers the capture pipeline, Room and its
  migrations, the shared database instance, the pure evaluator, encrypted transfer, the Quick
  Settings tile, and the widget.
- `build-debug.ps1` now freezes the APK as `dist/SignalRules-v<version>.apk` and clears earlier
  artifacts first, so the directory holds one binary and `SHA256SUMS.txt` names the version it
  describes.

### Added

- History metadata can be exported as CSV through Android's storage access, under Settings, Backup.
  The file carries exactly the columns the database holds, so no notification content is written,
  and every field is quoted because notification keys and group keys routinely contain commas.

- A history record can be kept past the retention period. Starring one exempts it from pruning
  until you unstar it, so a record worth holding on to does not need retention widened for
  everything else.

- A history record can open the app it came from. This uses the app's own launcher entry, not the
  notification's action, which this build neither stores nor fires. Package visibility is declared
  narrowly, for launchable activities only, rather than by asking to see every installed package.

- History now records what Android itself thought of each notification: channel importance, whether
  it is a conversation, the platform category, and whether it is ongoing. All four come from the
  system rather than from anything the notification said, and they are what tells a silent promotion
  apart from a priority conversation. History can filter on importance and on conversations, and
  each row shows the values it has.

- A warning when the listener has gone quiet. Access granted, the service reporting itself
  connected, and nothing captured for twelve hours is the shape an OEM battery manager leaves
  behind, and it used to look identical to a quiet day. The last capture time is now stored on
  disk, so the warning survives restarting the app or the phone.
- Per-manufacturer steps for keeping the listener bound, under "Rules are not triggering?" in
  Settings. Samsung, Xiaomi, Huawei, OnePlus, Oppo and Vivo get their own wording, everything else
  gets a generic list, and Android 13 and newer add the restricted-settings unlock.

- History now records which saved rules matched a notification when it arrived, so the
  "Rule-triggered" filter returns real records, each row names the rules that would have matched,
  and every rule reports how many recent notifications it would have caught. Only rule ids are
  stored: the notification's own text is evaluated while it is in memory and never persisted, and
  nothing is executed. A notification the system redacted is recorded as such rather than looking
  like an ordinary miss.

- An explainer for notifications the system redacted. Android 15 and newer hide the text of
  anything that looks like a sign-in code from every app that reads notifications, and the resulting
  history rows read as a bug in whichever app you are using. History records the system hid now
  offer "Why is content hidden?", matched by a row in Settings, covering what still matches, the
  Enhanced notifications switch, and the ADB command that grants the permission where that switch
  is missing. The command can be copied to the clipboard.

### Security

- Rule export moved to format 2, which records its own key-derivation parameters and raises PBKDF2
  from 120,000 iterations to 600,000, the OWASP floor for HMAC-SHA256. The parameters are
  authenticated alongside the ciphertext, an import refuses a file asking for an unreasonable
  derivation cost, and files written by earlier builds still import.

### Fixed

- The Dismissed history filter is shown as unavailable with its reason, the way every other
  unavailable control in the app is, instead of accepting a tap and always returning nothing.
  Rule-triggered became a working filter in the same release.

- History retention honours every period the dialog offers. "7 days" and "Forever" were selectable
  and remembered, but neither was implemented, so both silently pruned at thirty days. Choosing
  "Forever" now keeps everything, and the dialog is built from the periods the code can actually
  apply, so the two cannot drift apart again.

- A rule that tests no phrase now matches a notification carrying no title or text. Custom layouts,
  foreground-service notifications and summaries routinely carry neither, and an app-only rule was
  being refused for missing content it never asked for. Content the system redacted is still
  refused, because the hidden text might have matched either way.
- The stale-listener warning clears as soon as a notification arrives, instead of insisting the
  listener is dead until the screen is left and reopened.
- Each rule's match count is taken from all stored history rather than from whatever the History
  tab happens to be filtered to, which could report an active rule as idle.
- The last-capture time is written at most once a minute instead of on every notification, so a
  burst no longer means one full preferences rewrite per notification on the callback thread.
- A settings-recovery notice is shown once rather than repeating for every screen that opens
  afterwards in the same process.
- Captures that arrive before the saved rules have been read are recorded as such, so they cannot
  be misread as "your rules were checked and none matched".

- Repaired the PowerShell helpers on Windows PowerShell 5.1, the shell the README asks for. Reading
  a JDK version through a redirected `java -version` turned its banner into a terminating error, so
  every build, test, lint and validation script failed with a message that named no cause.
- A second loss of notification access is announced again even when the listener never managed to
  bind between the two, instead of the first notice spending the flag for the life of the process.
- The rule file's legacy format no longer accepts a key-derivation cost of its own choosing, which
  a file could otherwise use to make an import take tens of seconds with no way to cancel it.

- Stopped counting Android 16's own group summaries as notifications. The platform groups an app's
  notifications itself and posts a summary beside the children, so the widget count, the widget's
  last-capture time, and the history list all included a row that carried nothing of its own.
  Summaries are still reachable through the existing metadata filter.
- Asked the platform to rebind a listener that has granted access but has never called back, rather
  than treating that state as healthy. Nothing else moved the state out of unknown, so capture could
  stay dead while the app reported itself fine.
- Kept announcing a genuine revocation when the platform had already unbound the listener first.
  Revocation is now tracked separately from the connection state instead of being inferred from it.

- Raised kotlinx.serialization from 1.7.3 to 1.8.1. Room 2.8.4 asks for 1.8.1 and the older pin
  won the conflict, so the schema parser inside Room's migration test helper hit an
  AbstractMethodError on device.
- Made the instrumented test suite runnable for the first time. Dexing rejected every Kotlin test
  name containing spaces at this minimum SDK, so the whole androidTest source set failed to build,
  and the exported Room schemas were never packaged into the test APK. Nine instrumented tests now
  execute on a device.
- Gave the listener, the UI, and the widget one shared database handle. Room only notifies
  observers registered on the instance that performed a write, so history captured by the listener
  never reached the screen watching it. The widget also opened and closed its own database on every
  broadcast, which the listener sends after each captured notification.

- Stopped a resume from reporting a working notification listener as disconnected. The healthy
  case (access granted, listener connected) fell through to the revoked branch, so every return
  to the app published a disconnected listener, emitted an access-revoked event, and raised the
  health banner over a listener that was fine. The decision is now a pure function covered by a
  table test, and losing access announces itself once instead of on every resume.

## [1.3.1] - 2026-08-02

### Added

- Applied the persisted Theme setting to dark, light, and system-default palettes, with accessible
  preference controls, selectable dialogs, and minimum touch targets across shared UI components.
- Hardened validation provenance with a build-manifest hash check, explicit capture/comparison
  failure classes, fail-fast full validation, and Gaussian-windowed structural-similarity metrics.
- Made corrupted preference recovery deterministic on Windows and OEM filesystems by removing the
  unreadable payload before DataStore rewrites recovered defaults.
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
- Added a Quick Settings tile and in-app status for pausing metadata capture without revoking
  listener access; the persisted gate ignores callbacks before sanitization and restores on restart.
- Added an adaptive home-screen metadata widget showing only bounded count, timestamp,
  content-provenance, or paused state; listener writes request bounded widget refreshes.

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

## Roadmap archive — 2026-08-10 — ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# Roadmap — ANM / Signal Rules

Only incomplete work is listed here. This file was normalized on 2026-08-02: completed entries were removed, residual acceptance gaps were retained under their original IDs, and new research-driven items continue at R-042. Every item is traceable to `RESEARCH.md` and the cited repository evidence.

## Research-Driven Additions

### P0

### P1

### P2
```

</details>
