# Audit summary

## Outcome

The native BuzzKill product was mapped as a dark, rule-driven notification automation app with four persistent bottom destinations: Rules, History, Explore, and Settings. The audit captured onboarding, empty and populated Rules/History states, search, the rule sentence builder, nested content conditions, the complete visible action catalog, rule management menus, article/suggestion exploration, settings and dialogs, notification-history activity, lifecycle persistence, and landscape behavior.

No replacement implementation was started.

## Coverage

- 88 screen-state captures plus 1 primary-navigation recording.
- 76 native captures and 67 distinct native visual states.
- 26 native surface families: 12 full-screen/page surfaces, 9 dialogs, 5 menus, and 0 observed bottom sheets.
- 160 canonical native controls documented across 422 state-specific control instances.
- 15 primary flows: 8 fully tested, 4 partially tested, and 3 not tested for safety/dependency reasons.
- 88 generated JSON screen specifications.

See `testing/coverage-report.md` and `screens/screen-catalog.csv` for the counting method and exact omissions.

## Most important confirmed findings

1. Rules are expressed as a large, editable natural-language sentence. Underlined yellow spans are tap targets, while `+ Filter` and the main action are independent choices. Incomplete rules remain save-tappable but show red field styling and the exact warning, “You have a missing field. Please tap to fill it in to complete the rule.” Evidence: `029_rule_builder_default`, `043_rule_builder_filtered`, `059_rule_builder_validation_missing`, `062_rule_builder_complete`.
2. A rule consists observably of an app selector, a composable notification-content filter, zero or more additional contextual clauses, and one action. Content filters support phrases, extras, nested groups, and four any/all/negation operators. Evidence: `030_app_selector`, `032_condition_match_type_dialog`, `036_extras_filter_selector`, `039_filter_group_default`, `040_filter_operator_dialog`, `042_filter_phrase_added`.
3. The action selector is a scrollable categorized catalog. Visible actions include Cooldown, Mute, Alarm, Pocket check, Remind me, Speak, Unsilence, Add snooze button, Batch, Batch every, Custom alert, Flashlight, Secret, Sticky, Summarize, Add share button, Dismiss, Keep if, Undo dismiss, Open notification, Press button, Reply, Copy verification code, Remove from history, Restore after reboot, Set ringer, Trigger MacroDroid, Trigger Tasker, and Multi-tool. Evidence: `049_action_selector_top` through `058_action_selector_bottom_2`.
4. Saved rules are cards with a More menu and Enabled/Disabled switch. Disabled cards become gray and strike through the entire rule sentence. A disabled test rule survived background/resume, force-stop/relaunch, and rotation. Evidence: `063_rules_populated_test_record`, `064_rules_test_record_disabled`, `065_rule_overflow_menu`, `085_cold_relaunch_native_state`, `087_rules_landscape_native`.
5. History combines a daily count/timeline with two segmented controls: all versus rule-triggered notifications, and sent versus dismissed. Item menus expose Restore, Open notification, View activity, Copy, Create rule, and Delete. The activity page has Rules and Changes tabs. Evidence: `013_history_empty`, `071_history_populated_test_notification`, `072_history_notification_detail`, `073_history_item_activity`, `074_history_item_changes`, `075_history_rule_triggered_filter_empty`, `076_history_dismissed_filter_empty`.
6. Onboarding requires POST_NOTIFICATIONS, an Android background/device-idle exemption, and notification-listener access. After completion, the listener is live as a foreground service using channel `internal`. Evidence: `002_welcome_default` through `009_notification_access_confirmation`, `evidence/package/device-idle-target-summary-20260801-1413.txt`, `evidence/package/running-services-target-summary-20260801-1410.txt`.
7. The visual system is dominated by `#0A0B0D` background, `#1A1C21` bottom/surface color, `#FFF387` yellow, white primary text, `#858586` secondary text, `#FF7070` validation red, blue `#93D1F3` enabled-rule cards, and `#3F414B` disabled-rule cards. Evidence: `evidence/measurements/color-analysis.json`.

## Highest-risk unknowns

- Actual execution/configuration behavior for most actions was not triggered. Alarm, automatic reply/button press, open-notification automation, flashlight, clipboard, Tasker/MacroDroid, ringer/DND, full-screen intent, biometric, location, Bluetooth, and reboot actions remain `UNKNOWN` beyond their visible descriptions.
- Import/export/automatic-backup serialization and conflict behavior remain `UNKNOWN`; the Android folder picker was opened and safely canceled.
- Light theme, dynamic text scaling, TalkBack announcements/focus order, and non-English translated layouts were not activated.
- Backend/network architecture is `UNKNOWN`. No interception, endpoint probing, or TLS modification was performed.
- Exact font family and proprietary illustration/icon provenance are `UNKNOWN`; future work must use authorized or original replacements.
- A true process-cold launch could not be isolated while notification-listener access remained enabled: `am start -W` reported `WARM` after force-stop because Android restarted/retained the listener process. The measured 6.447 s relaunch was also contaminated by the excluded repackaging overlay.

## Missing states and flows

Loading and recoverable network-error states were not observed during native browsing. Destructive controls (delete rule/history/all rules), irreversible/account-affecting actions, automatic notification actions, external integration completion, backup file selection, and Chrome onboarding were not executed. Exact reasons are in `testing/untested-and-blocked-cases.md`.

## Recommended reconstruction order

1. Theme/tokens, navigation shell, and reusable cards/segmented controls.
2. Observable data model and persistence for Rule, Filter, Action, and HistoryItem.
3. Rules empty/populated surfaces and read-only sentence renderer.
4. Rule builder, app selector, filter composer, validation, and action catalog.
5. History timeline, search, filters, item menu, Rules/Changes activity page.
6. Explore article/suggestion surfaces and suggestion-to-builder preview.
7. Settings and native dialogs, followed by permission orchestration.
8. Notification-listener runtime and actions in risk order, each behind contract tests.
9. Accessibility, text scaling/localization, lifecycle, landscape, and performance hardening.

Exact implementation backlog: `planning/implementation-backlog.md`.
