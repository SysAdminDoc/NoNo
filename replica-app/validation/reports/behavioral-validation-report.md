# Behavioral validation report

Date: 2026-08-29  
Device: `emulator-5566` (`irlstreamer_drain_api35`, Android 15/API 35, 1080 × 2400, 420 dpi)

## Automated results

- Unit tests: **PASS**, 131 tests, 0 failures.
- Android lint: **PASS**.
- Instrumentation: **PASS**, 16 tests, 0 failures on the validation emulator.
- Build/install/launch: **PASS** for `com.sysadmindoc.nono.debug`.

## Flow coverage

| Audit flow | Result | Evidence/limitation |
|---|---|---|
| F01 First launch onboarding | PASS | Actual Android notification permission, battery exemption, notification-listener settings, warning, return, and automatic home transition were exercised. |
| F02 Primary navigation and empty states | PASS | All four roots and empty states captured; Compose semantics present. |
| F03 Create rule and content filter | PASS | App/phrase/filter states captured; sentence and validation unit tests pass; saved rule persists. |
| F04 Action catalog and selection | PASS | All 29 action entries and scroll states reproduced; selection is local. |
| F05 Save and manage rule | PARTIAL | Save, enable/disable, rename, duplicate, and delete model paths exist; folder/priority/duration effects are not persisted because commit behavior was not safely confirmed. |
| F06 History empty and search | PASS | Empty, search, no-result, and filtering logic validated. |
| F07 History populated and item actions | PARTIAL | Deterministic record/detail/activity works; real restore/open/reply/delete side effects are simulated or untested. |
| F08 Explore articles and scroll | PARTIAL | Native list/scroll/handoff works; external article contents and original editorial assets are excluded. |
| F09 Explore suggestion preview | PASS | Deterministic suggestion opens a populated rule builder. |
| F10 Settings and dialogs | PARTIAL | All observed sections/dialogs and preference persistence work; destructive and integration effects remain safe simulations. |
| F11 Backup import/export | NOT IMPLEMENTED | Android picker handoff works; serialization and scheduled backups were not observable. |

Totals: 6 fully validated, 4 partially validated, 1 not implemented.

## Lifecycle and persistence evidence

- Onboarding completion, saved rule, and enabled/disabled state survived force-stop/relaunch. Evidence: `lifecycle-populated-relaunch.xml`, `lifecycle-disabled-relaunch.xml`, and the corresponding screenshots under `validation/current`.
- A persisted Settings switch changed from checked to unchecked and remained unchecked after a cold relaunch. Evidence: `settings-persistence-before.xml` and `settings-persistence-after.xml`.
- The real capability path re-queried Android state on resume and automatically reached the Rules home after all three grants. Evidence: `permission-step-0.xml`, `permission-notification-dialog.xml`, `permission-battery-dialog.xml`, `permission-listener-settings.xml`, `permission-listener-warning.xml`, and `permission-complete-home.xml`.
- Temporary notification access, notification permission, and battery exemption granted for this test were removed afterward. Replica data was reset; the original audited app was not modified.
- Portrait/landscape capture restored the prior `free` user-rotation mode and `accelerometer_rotation=1`.

## Performance observations

- Measured cold launch after install: approximately 1.2 to 2.3 seconds on this emulator.
- Warm delivery to the already-running top activity: approximately 13 ms in the sampled run.
- Sample total PSS after launch: approximately 101 MB; device/emulator-specific.
- No crash, ANR, or visible freeze occurred during the 76-state capture sweep. The three-frame `gfxinfo` launch sample was too small for a meaningful scrolling-jank conclusion.

## Safety boundary

Notification contents are not logged or transmitted. The local listener records only package name, count, and timestamp. Actions capable of changing third-party notifications or device state remain deterministic local simulations.
