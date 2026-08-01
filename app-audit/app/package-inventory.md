# Package inventory

## Package facts

`CONFIRMED`: Base-only package, code path recorded by `pm path`, target SDK 35, min SDK 24, resizable via SDK, supports small through xlarge screens and any density, and declares backup allowed. Evidence: `evidence/package/dumpsys-package-20260801-140502.txt` and `apk-paths-20260801-140502.txt`.

The package dump reported a primary ABI of `arm64-v8a` for the installed artifact while the emulator system image is x86_64. This is an environment fact, not a reconstruction requirement.

## Resolver-visible components

The following are components directly reported by Android resolver tables. They are not a complete manifest inventory; non-resolver-visible components remain `UNKNOWN` without static analysis.

### Activities

- `.ui.MainActivity` — launcher (`MAIN`/`LAUNCHER`).
- `androidx.core.google.shortcuts.TrampolineActivity` — shortcut listener action.
- `.plugins.macrodroid.NotificationEventRunner$NotificationEventActivity` — Locale/MacroDroid condition-edit action.
- `.integrations.ToggleRuleConfigurationActivity` — Locale/Tasker setting-edit action.

### Receivers

- `.background.receivers.BootReceiver` — `BOOT_COMPLETED`.
- Tasker/Locale action and condition receivers.
- AndroidX profile installer receiver actions.

### Services

- `.background.service.NotificationService` — notification-listener service requiring `BIND_NOTIFICATION_LISTENER_SERVICE`; live and foreground during the audit.
- `.background.accessibility.WorkaroundAccessibilityService` — accessibility service requiring `BIND_ACCESSIBILITY_SERVICE`; not enabled as a user accessibility service in the baseline.
- `.background.wear.MessageListenerService` — wearable message listener with `wear:` scheme.
- `.integrations.shortcuts.TriggerTileService` and `.integrations.shortcuts.RestoreBatchTileService` — Quick Settings tiles.
- Tasker/Locale action and condition intent services.

### Providers

- AndroidX `FileProvider` authority `com.samruston.buzzkill.provider`.
- ML Kit initialization provider.
- AndroidX Startup initialization provider.

Evidence for all resolver-visible components: `evidence/package/dumpsys-package-20260801-140502.txt`.

## Runtime services and jobs

`CONFIRMED`: NotificationService was bound by Android, foreground, and used ongoing notification channel `internal`. No other active target service appeared in the scoped runtime dump. Evidence: `evidence/package/running-services-target-summary-20260801-1410.txt`.

`CONFIRMED AT CAPTURE TIME`: No target JobScheduler job was registered in `jobscheduler-20260801-140502.txt`. This does not prove the app never schedules jobs.

## Notification channels

Android reported five target channels: `hidden`, `priority` (“Important”), `silent_important` (“Silent”), `alarm`, and `internal`, with measured importance/sound/badge behavior. Evidence: `evidence/package/notification-target-summary-20260801-1405.txt`.

## Export/deep-link limitations

`CONFIRMED`: Resolver tables expose Tasker/Locale integration actions and a wearable service scheme. No browser/http app deep link was reported.

`UNKNOWN`: Complete exported flags, every activity/service/receiver, all provider path permissions, and non-resolver-visible intent filters cannot be established from allowed dynamic package inspection alone.
