# Coverage report

## Quantitative coverage

| Metric | Count | Counting rule |
|---|---:|---|
| Screen-state captures | 88 | Screen catalog rows |
| Flow recordings | 1 | Manifest flow-recording row |
| Native captures | 76 | `scope=native` |
| Distinct native visual states | 67 | Native captures minus explicitly cataloged repeat/returned states |
| Native surface families | 26 | Distinct `unique_surface_group` values |
| Full-screen/page surfaces | 12 | Surface families excluding dialogs/menus |
| Dialogs | 9 | Distinct native modal-dialog families |
| Menus | 5 | Distinct anchored/popup-menu families |
| Bottom sheets | 0 | None observed |
| Canonical interactive controls | 160 | De-duplicated by surface + resource/label + type |
| State-specific control instances | 422 | Interactive nodes in distinct native visual states |
| Primary flows | 15 | `flows/flow-catalog.csv` |
| Fully tested flows | 8 | Safe path completed end-to-end |
| Partially tested flows | 4 | Safe portions completed; material branch omitted |
| Not tested flows | 3 | Action/integration/backup completion blocked by safety or dependency |
| Unresolved open questions | 24 | `planning/open-questions.md` |

Generated counting sources: `screens/screen-catalog.csv` and `screens/screen-specs/*.json`.

## State coverage

| State | Result |
|---|---|
| Default | Captured for onboarding, four root pages, settings, builder, selectors, and dialogs |
| Populated | Captured for Rules, History, content filter, action selection, and suggestion preview |
| Empty | Captured for Rules, History, search, recent matches, activity, shortcut editor |
| Loading | Not observed; no safe deterministic native loading state found |
| Error | System folder error captured; no native network/runtime error observed |
| Disabled | Disabled rule and disabled shortcut action captured |
| Selected | Tabs, segmented controls, switches, radio dialogs, Mute action captured |
| Validation | Missing rule field captured |

## Scope exclusions

- 3 LiteAPKS/repackaging overlay captures are preserved as provenance and excluded from native counts/design.
- 2 Chrome first-run captures document external handoff only.
- 6 Android system captures document permission/settings/folder-picker dependencies.
- 1 Android launcher baseline capture documents the emulator starting state.

## Missing coverage and exact reasons

- Permission-denial branches: not selected because the objective required a functioning notification audit; denial can be tested later on a disposable clone without clearing this state.
- Loading/network errors: no deterministic native request was observable; network disabling was outside the authorized pass.
- Destructive actions: delete rule/history/all rules were explicitly prohibited.
- Purchases/authentication: none attempted; supplied APK required no account.
- Automated actions: could change device state, open apps, send replies, copy sensitive content, or create alarms; descriptions were recorded without execution.
- Backup/import/export: selection/serialization could overwrite or introduce data; folder picker was safely canceled.
- External articles/help/community: Chrome first-run intercepted; no browser account/onboarding changes were made.
- Accessibility service/TalkBack: not enabled because system-wide accessibility changes were not authorized. UI Automator semantics were inspected read-only.
- Light theme/locales/font scaling: preference choices were inspected but not applied to avoid broad state changes and combinatorial expansion.
- True cold start: notification-listener process remained live/restarted; revoking it would alter the core configured state.

## Completion interpretation

The audit is complete for observable native structure and safe core flows, not for every side-effecting automation. Unknowns are explicitly carried into reconstruction acceptance criteria and are not silently guessed.
