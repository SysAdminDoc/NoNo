# Final coverage report

This is the canonical count. The behavioral and visual reports cover one dimension each and are
summarised here; where a number appears in more than one place, this file is the one to trust.

Every number below names the run it came from. The unit total is from a forced full run on
**2026-09-05** (`scripts/run-unit-tests.ps1`, which re-runs the suite rather than accepting an
up-to-date task, and refuses to record evidence for a suite that did not execute); its machine-readable
form is `validation/reports/unit-test-results.json`. The instrumentation total is from **2026-08-31**
and has not been re-measured since; the suite has grown since that run, so the source count and the
last measured count are given separately rather than merged. The visual figures are from the
2026-08-31 capture sweep.

## Outcome

The Android project builds, installs, launches, and exposes all 76 native audit states through real Compose UI. Eight audited system/browser rows are implemented as public-intent handoffs; three LiteAPKS/repackaging rows are excluded as requested; the emulator baseline is not application scope.

This is a functional clean-room reconstruction with material visual and integration gaps, not an exact replica.

## Coverage counts

| Category | Count | Result |
|---|---:|---|
| Audit traceability rows | 88 | 88 mapped |
| Native state captures implemented | 76 | 76 implemented |
| External system/browser handoffs | 8 | 8 mapped/implemented |
| Excluded repackaging states | 3 | Excluded by request |
| Baseline-only state | 1 | Not applicable |
| Native visual comparisons produced | 76 | 76 compared |
| Configured 0.85 threshold passes | 67 | 88.2% |
| Configured threshold misses | 9 | Documented |
| Preferred 0.985 target passes | 0 | Target not achieved |
| Fully behaviorally validated flows | 7 | PASS |
| Partially validated flows | 4 | PARTIAL |
| Not implemented flows | 0 | - |
| Unit tests (run 2026-09-05) | 469 | PASS, 0 failures |
| Instrumentation tests (last run 2026-08-31) | 70 | PASS, 0 failures |
| Instrumentation tests present in source | 109 | Not re-measured since 2026-08-31 |

## Visual results

- Mean pixel similarity: **0.888507**.
- Range: **0.72754331-0.98499503**.
- 67/76 pass the configured 0.85 per-screen threshold.
- 33/76 are at least 0.90; 3/76 are at least 0.95; 0/76 reach 0.985.
- All images matched baseline dimensions. Masks cover only system-bar regions documented in `validation/masks`; inaccurate app content was not masked.

Threshold misses:

- `002_welcome_default`: 0.77191235
- `004_welcome_notifications_granted`: 0.77637314
- `006_welcome_background_allowed`: 0.80029209
- `077_explore_scrolled_1`: 0.78545068
- `078_explore_scrolled_2`: 0.76715792
- `079_explore_scrolled_3`: 0.73110806
- `080_explore_bottom`: 0.72754331
- `081_explore_bottom_2`: 0.75362387
- `082_explore_suggestion_rule_preview`: 0.82950221

The misses are concentrated in onboarding and Explore/suggestion content where the original identity, illustrations, and editorial material were not authorized. Rule management, condition/filter/action selectors, dialogs, history, settings, relaunch, and landscape states pass the configured threshold.

## Quality gates

- Environment: PASS
- Kotlin/Compose debug build: PASS
- Android lint: PASS
- Unit tests: PASS
- On-device instrumentation: PASS as of 2026-08-31; not re-run since
- Streamed APK installation: PASS
- Cold launch: PASS
- Full 76-state capture: PASS
- Visual threshold gate: FAIL by design for the nine documented states
- Permission/capability flow: PASS; temporary grants restored
- Rule and Settings persistence: PASS

## Material gaps

- Independent name, icon, code-native artwork, article summaries, and system font differ from the original.
- The prompt's preferred 0.985 visual target was not achieved.
- Real notification-changing integrations are simulated locally.
- Rule serialization and launcher-shortcut pinning are implemented. Scheduled backup is implemented as of 1.5.0 and runs on a timer through WorkManager: a copy of the saved rules is written to a user-picked folder daily or weekly without the app open. It encrypts with a key held in the device keystore, because a job on a timer has nobody to ask for a passphrase, so those files restore only on the device that wrote them; the passphrase-protected export remains the way to move rules between devices. Notification history is never included.
- Destructive, financial, privacy-sensitive, external-content, and permission-denial branches remain safely untested or disabled.
- Exact animation timing and full TalkBack traversal were not exhaustively measured.

## Evidence locations

- Traceability: `docs/audit-traceability-matrix.csv`
- Known deviations: `docs/known-deviations.md`
- Visual aggregate: `validation/reports/visual-validation-report.md`
- Per-screen metrics/diffs/heatmaps: `validation/diffs`
- Overlays: `validation/overlays`
- Behavior: `validation/reports/behavioral-validation-report.md`
- Full runner result: `validation/reports/full-validation.json`
- Unit/UI/lint HTML: `app/build/reports`

Verdict: **PARTIAL** because the measured visual target and several production integrations remain incomplete.
