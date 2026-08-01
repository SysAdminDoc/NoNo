# Untested and blocked cases

## Prohibited or destructive

- Delete individual rule.
- Delete history item.
- Delete all rules.
- Complete any flow that could remove or overwrite user data.
- Real message send/reply, real form submission, purchases, account changes.

Controls are documented from `065_rule_overflow_menu`, `072_history_notification_detail`, and `020_settings_scrolled_4`; results remain `UNTESTED`.

## Privacy/device-side effects

- Automatic Reply, Press button, Open notification, Copy verification code, Copy, share.
- Alarm, exact alarm, full-screen alarm, flashlight, ringer/DND, custom sound/vibration, biometric/secret handling.
- Location, Bluetooth, sensor, pocket/face-down/on-table conditions.
- Restore after reboot, Restore batch, reboot behavior.

Visible descriptions are `CONFIRMED`; configuration and execution are `UNKNOWN`.

## External dependencies

- Tasker, MacroDroid, Wear OS, Quick Settings tile completion.
- Reddit/community, support email, guide/FAQ article content.
- Import/export/automatic-backup file format and conflict handling.
- Open notification settings result.

## State combinations not exercised

- Denied notification/background/listener permissions.
- Light/follow-system theme.
- Alternate locale and translated overflow.
- 200% font scale/TalkBack/keyboard-only navigation.
- Multiple rules, folder grouping, priority reordering, duplicate rules.
- Large notification history, pagination/virtualization, multi-day timeline.
- Offline/network failures and retry.
- App update/migration and Android reboot.
- Tablet/foldable/multi-window.

## Non-native artifact

The supplied APK displayed LiteAPKS advertising on launch and rotation. It was intentionally ignored and excluded from reconstruction. It also contaminates cold-start timing. Evidence is retained only so future auditors can distinguish distribution-artifact behavior from native BuzzKill behavior.
